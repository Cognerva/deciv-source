package com.unciv.logic.event

import com.unciv.logic.event.EventBus.send
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

/**
 * The heart of the event system. Significant game events are sent/received here.
 *
 * Use [send] to send events and [EventReceiver.receive] to receive events.
 *
 * **Do not use this for every communication between modules**. Only use it for events that might be relevant for a wide variety of modules or
 * significantly affect the game state, i.e. buildings being created, units dying, new multiplayer data available, etc.
 */
@Suppress("UNCHECKED_CAST") // Through using the class-keyed map, we ensure all methods get called with correct argument type
object EventBus {

    // Use Java class metadata here instead of KClass.supertypes. The latter needs kotlin-reflect,
    // which is not available in the RoboVM runtime used by iOS.
    private val listeners = mutableMapOf<Class<*>, MutableList<EventListenerWeakReference<*>>>()

    /**
     * Only use this from the render thread. For example, in coroutines launched by [com.unciv.ui.crashhandling.launchCrashHandling]
     * always wrap the  call in [com.unciv.ui.crashhandling.postCrashHandlingRunnable].
     *
     * We could use a generic method like `sendOnRenderThread` or make the whole event system asynchronous in general,
     * but doing it like this makes debugging slightly easier.
     */
    fun <T : Event> send(event: T) {
        val eventListeners = getListeners(event.javaClass) as Set<EventListener<T>>
        for (listener in eventListeners) {
            val filter = listener.filter
            if (filter == null || filter(event)) {
                listener.eventHandler(event)
            }
        }
    }

    private fun getListeners(eventClass: Class<*>): Set<EventListener<*>> {
        val classesToListenTo = getClassesToListenTo(eventClass)
        // Set because we don't want to notify the same listener multiple times
        return buildSet {
            for (classToListenTo in classesToListenTo) {
                addAll(updateActiveListeners(classToListenTo))
            }
        }
    }

    /** To be able to listen to an event class and get notified even when child classes are sent as an event */
    private fun getClassesToListenTo(eventClass: Class<*>): List<Class<*>> {
        return (getSuperClasses(eventClass) + eventClass).distinct()
    }

    private fun getSuperClasses(javaClass: Class<*>): List<Class<*>> {
        val superClasses = ArrayList<Class<*>>()
        val superClass = javaClass.superclass
        if (superClass != null && superClass != Any::class.java) {
            superClasses += getSuperClasses(superClass)
            superClasses += superClass
        }
        for (interfaceClass in javaClass.interfaces) {
            if (interfaceClass == Any::class.java) continue
            superClasses += getSuperClasses(interfaceClass)
            superClasses += interfaceClass
        }
        return superClasses.distinct()
    }

    /** Removes all listeners whose WeakReference got collected and returns the ones that are still active */
    private fun updateActiveListeners(eventClass: Class<*>): List<EventListener<*>> {
        return buildList {
            val listenersWeak = listeners[eventClass] ?: return listOf()
            val iterator = listenersWeak.listIterator()
            while (iterator.hasNext()) {
                val listener = iterator.next()
                val eventHandler = listener.eventHandler.get()
                if (eventHandler == null) {
                    // eventHandler got garbage collected, prevent WeakListener memory leak
                    iterator.remove()
                } else {
                    add(EventListener(eventHandler, listener.filter.get()))
                }
            }
        }
    }


    private fun <T: Event> receive(eventClass: KClass<T>, filter: ((T) -> Boolean)? = null, eventHandler: (T) -> Unit) {
        val javaClass = eventClass.java
        if (listeners[javaClass] == null) {
            listeners[javaClass] = mutableListOf()
        }
        listeners[javaClass]!!.add(EventListenerWeakReference(eventHandler, filter))
    }

    private fun cleanUp(eventHandlers: Map<KClass<*>, MutableList<Any>>) {
        for ((kClass, toRemove) in eventHandlers) {
            val registeredListeners = listeners.get(kClass.java)
            registeredListeners?.removeAll {
                val eventHandler = it.eventHandler.get()
                eventHandler == null || (eventHandler as Any) in toRemove
            }
        }
    }

    /**
     * Used to receive events by the [EventBus].
     *
     * Usage:
     *
     * ```
     * class SomeClass {
     *     private val events = EventReceiver()
     *
     *     init {
     *         events.receive(SomeEvent::class) {
     *             // do something when the event is received.
     *         }
     *     }
     *
     *     // Optional
     *     cleanup() {
     *         events.stopReceiving()
     *     }
     * }
     * ```
     *
     * The [stopReceiving] call is optional. Event listeners will be automatically garbage collected. However, garbage collection is non-deterministic, so it's
     * possible that the events keep being received for quite a while even after a class is unused. [stopReceiving] immediately cleans up all listeners.
     *
     * To have event listeners automatically garbage collected, we need to use [WeakReference]s in the event bus. For that to work, though, the class
     * that wants to receive events needs to hold references to its own event listeners. [EventReceiver] allows to do that while also providing the
     * interface to start receiving events.
     */
    class EventReceiver {

        val eventHandlers = mutableMapOf<KClass<*>, MutableList<Any>>()
        val filters: MutableList<Any> = mutableListOf()

        /**
         * Listen to the event with the given [eventClass] and all events that subclass it. Use [stopReceiving] to stop listening to all events.
         *
         * The listeners will always be called on the main GDX render thread.
         *
         * @param T The event class holding the data of the event, or simply [Event].
         */
        fun <T: Event> receive(eventClass: KClass<T>, filter: ((T) -> Boolean)? = null, eventHandler: (T) -> Unit) {
            if (filter != null) {
                filters.add(filter)
            }
            if (eventHandlers[eventClass] == null) {
                eventHandlers[eventClass] = mutableListOf()
            }
            eventHandlers[eventClass]!!.add(eventHandler)

            EventBus.receive(eventClass, filter, eventHandler)
        }

        /**
         * Stops receiving all events, cleaning up all event listeners.
         */
        fun stopReceiving() {
            cleanUp(eventHandlers)
            eventHandlers.clear()
            filters.clear()
        }
    }

}

/** Exists so that eventHandlers and filters do not get garbage-collected *while* we are passing them around in here,
 * otherwise we would only need [EventListenerWeakReference] */
private class EventListener<T>(
    val eventHandler: (T) -> Unit,
    val filter: ((T) -> Boolean)? = null
)

private class EventListenerWeakReference<T>(
    eventHandler: (T) -> Unit,
    filter: ((T) -> Boolean)? = null
) {
    val eventHandler = WeakReference(eventHandler)
    val filter = WeakReference(filter)
}
