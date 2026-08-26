package com.unciv.app.ios;

import io.ktor.client.engine.HttpClientEngine;
import io.ktor.client.engine.HttpClientEngineBase;
import io.ktor.client.engine.HttpClientEngineCapability;
import io.ktor.client.engine.HttpClientEngineConfig;
import io.ktor.client.engine.HttpClientEngineFactory;
import io.ktor.client.engine.UtilsKt;
import io.ktor.client.plugins.HttpTimeoutCapability;
import io.ktor.client.plugins.HttpTimeoutConfig;
import io.ktor.client.request.HttpRequestData;
import io.ktor.client.request.HttpResponseData;
import io.ktor.http.HeadersBuilder;
import io.ktor.http.HttpProtocolVersion;
import io.ktor.http.HttpStatusCode;
import io.ktor.http.content.OutgoingContent;
import io.ktor.util.date.DateJvmKt;
import io.ktor.utils.io.jvm.javaio.BlockingKt;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.jvm.functions.Function1;
import kotlinx.coroutines.DisposableHandle;
import kotlinx.coroutines.Job;
import io.ktor.utils.io.ByteChannel;
import org.robovm.apple.foundation.NSData;
import org.robovm.apple.foundation.NSError;
import org.robovm.apple.foundation.NSHTTPURLResponse;
import org.robovm.apple.foundation.NSMutableURLRequest;
import org.robovm.apple.foundation.NSURL;
import org.robovm.apple.foundation.NSURLResponse;
import org.robovm.apple.foundation.NSURLSession;
import org.robovm.apple.foundation.NSURLSessionDataTask;
import org.robovm.objc.block.VoidBlock3;

/** Ktor engine backed by Apple's URL loading system for RoboVM. */
public final class IOSHttpClientEngine extends HttpClientEngineBase {
    public static final Factory Factory = new Factory();
    private static final Timer REQUEST_TIMEOUT_TIMER = new Timer("iOS HTTP request timeout", true);
    private static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L;
    // Ktor represents an infinite timeout with Long.MAX_VALUE. Passing that to
    // Timer.schedule causes the absolute deadline calculation to overflow.
    // NSURLSession's resource timeout is seven days, so use the same safe upper
    // bound for the platform watchdog as well.
    private static final long MAX_SAFE_TIMEOUT_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private final HttpClientEngineConfig config;

    private IOSHttpClientEngine(HttpClientEngineConfig config) {
        super("iOS");
        this.config = config;
    }

    @Override
    public HttpClientEngineConfig getConfig() {
        return config;
    }

    @Override
    public Set<HttpClientEngineCapability<?>> getSupportedCapabilities() {
        return Collections.<HttpClientEngineCapability<?>>singleton(HttpTimeoutCapability.INSTANCE);
    }

    @Override
    public Object execute(
        final HttpRequestData data,
        final Continuation<? super HttpResponseData> continuation
    ) {
        final CoroutineContext callContext = (CoroutineContext) UtilsKt.callContext((Continuation) continuation);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<NSURLSessionDataTask> taskReference = new AtomicReference<>();
        final AtomicReference<DisposableHandle> cancellationHandle = new AtomicReference<>();
        final AtomicReference<TimerTask> timeoutTaskReference = new AtomicReference<>();
        try {
            Job job = (Job) callContext.get(Job.Key);
            if (job != null && !job.isActive()) {
                throw job.getCancellationException();
            }

            final NSMutableURLRequest request = new NSMutableURLRequest(new NSURL(data.getUrl().toString()));
            request.setHTTPMethod(data.getMethod().getValue());

            HttpTimeoutConfig timeout = data.getCapabilityOrNull(HttpTimeoutCapability.INSTANCE);
            Long timeoutMillis = timeout == null ? null : timeout.getRequestTimeoutMillis();
            long timeoutForRequest = timeoutMillis == null ? DEFAULT_REQUEST_TIMEOUT_MILLIS : timeoutMillis;
            long safeTimeoutMillis = timeoutForRequest <= 0L
                ? DEFAULT_REQUEST_TIMEOUT_MILLIS
                : Math.min(timeoutForRequest, MAX_SAFE_TIMEOUT_MILLIS);
            request.setTimeoutInterval(safeTimeoutMillis / 1000.0);

            for (Map.Entry<String, List<String>> header : data.getHeaders().entries()) {
                request.setHTTPHeaderField(header.getKey(), join(header.getValue()));
            }

            byte[] body = contentBytes(data.getBody());
            if (body != null) request.setHTTPBody(new NSData(body));

            if (job != null) {
                cancellationHandle.set(job.invokeOnCompletion(new Function1<Throwable, Unit>() {
                    @Override
                    public Unit invoke(Throwable cause) {
                        if (cause != null && completed.compareAndSet(false, true)) {
                            NSURLSessionDataTask task = taskReference.get();
                            if (task != null) task.cancel();
                        }
                        return Unit.INSTANCE;
                    }
                }));
            }

            TimerTask timeoutTask = new TimerTask() {
                @Override
                public void run() {
                    if (!completed.compareAndSet(false, true)) return;
                    NSURLSessionDataTask task = taskReference.get();
                    if (task != null) task.cancel();
                    DisposableHandle handle = cancellationHandle.get();
                    if (handle != null) handle.dispose();
                    resume(continuation, ResultKt.createFailure(
                        new RuntimeException("iOS HTTP request timed out")
                    ));
                }
            };
            timeoutTaskReference.set(timeoutTask);
            REQUEST_TIMEOUT_TIMER.schedule(timeoutTask, Math.max(1L, safeTimeoutMillis));

            NSURLSessionDataTask task = NSURLSession.getSharedSession().newDataTask(
                request,
                new VoidBlock3<NSData, NSURLResponse, NSError>() {
                    @Override
                    public void invoke(NSData bodyData, NSURLResponse response, NSError error) {
                        if (!completed.compareAndSet(false, true)) return;
                        TimerTask timeoutTask = timeoutTaskReference.get();
                        if (timeoutTask != null) timeoutTask.cancel();
                        DisposableHandle handle = cancellationHandle.get();
                        if (handle != null) handle.dispose();
                        try {
                            if (error != null) {
                                resume(continuation, ResultKt.createFailure(
                                    new RuntimeException(error.getLocalizedDescription())
                                ));
                                return;
                            }
                            if (!(response instanceof NSHTTPURLResponse)) {
                                throw new IllegalStateException("iOS HTTP response was not an HTTP response");
                            }

                            NSHTTPURLResponse httpResponse = (NSHTTPURLResponse) response;
                            HeadersBuilder responseHeaders = new HeadersBuilder();
                            for (Map.Entry<String, String> header : httpResponse.getAllHeaderFields().entrySet()) {
                                // NSURLSession transparently decompresses gzip responses, so the
                                // wire Content-Length and Content-Encoding no longer describe the
                                // NSData delivered to this callback. Ktor otherwise compares the
                                // stale compressed length with the decoded body and rejects valid
                                // responses (notably the Github mod-category list).
                                if ("Content-Length".equalsIgnoreCase(header.getKey())
                                    || "Content-Encoding".equalsIgnoreCase(header.getKey())) continue;
                                responseHeaders.append(header.getKey(), header.getValue());
                            }

                            byte[] bytes = bodyData == null ? new byte[0] : bodyData.getBytes();
                            responseHeaders.append("Content-Length", Integer.toString(bytes.length));
                            // The response is already fully buffered by NSURLSession. Feeding it
                            // through RawSourceChannel can leave its EOF suspended on RoboVM;
                            // use a closed in-memory channel so bodyAsText/bodyAsBytes complete.
                            ByteChannel channel = new ByteChannel(true);
                            channel.getWriteBuffer().write(bytes, 0, bytes.length);
                            channel.close();
                            resume(continuation, new HttpResponseData(
                                HttpStatusCode.Companion.fromValue((int) httpResponse.getStatusCode()),
                                DateJvmKt.GMTDate(Long.valueOf(System.currentTimeMillis())),
                                responseHeaders.build(),
                                HttpProtocolVersion.Companion.getHTTP_1_1(),
                                channel,
                                callContext
                            ));
                        } catch (Throwable throwable) {
                            resume(continuation, ResultKt.createFailure(throwable));
                        }
                    }
                }
            );
            taskReference.set(task);
            if (job != null && !job.isActive()) {
                throw job.getCancellationException();
            }
            task.resume();
        } catch (Throwable throwable) {
            NSURLSessionDataTask task = taskReference.get();
            if (task != null) task.cancel();
            if (completed.compareAndSet(false, true)) {
                TimerTask timeoutTask = timeoutTaskReference.get();
                if (timeoutTask != null) timeoutTask.cancel();
                DisposableHandle handle = cancellationHandle.get();
                if (handle != null) handle.dispose();
                resume(continuation, ResultKt.createFailure(throwable));
            }
        }

        return IntrinsicsKt.getCOROUTINE_SUSPENDED();
    }

    private void resume(Continuation<? super HttpResponseData> continuation, Object result) {
        continuation.resumeWith(result);
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private static byte[] contentBytes(OutgoingContent content) throws Exception {
        if (content == null || content instanceof OutgoingContent.NoContent) return null;
        if (content instanceof OutgoingContent.ByteArrayContent) {
            return ((OutgoingContent.ByteArrayContent) content).bytes();
        }
        if (content instanceof OutgoingContent.ContentWrapper) {
            return contentBytes(((OutgoingContent.ContentWrapper) content).delegate());
        }
        if (content instanceof OutgoingContent.ReadChannelContent) {
            InputStream stream = BlockingKt.toInputStream(
                ((OutgoingContent.ReadChannelContent) content).readFrom(), null
            );
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                return bytes.toByteArray();
            } finally {
                stream.close();
            }
        }
        throw new UnsupportedOperationException(
            "iOS HTTP engine cannot send " + content.getClass().getName()
        );
    }

    public static final class Factory implements HttpClientEngineFactory<HttpClientEngineConfig> {
        @Override
        public HttpClientEngine create(Function1<? super HttpClientEngineConfig, Unit> block) {
            HttpClientEngineConfig config = new HttpClientEngineConfig();
            block.invoke(config);
            return new IOSHttpClientEngine(config);
        }
    }
}
