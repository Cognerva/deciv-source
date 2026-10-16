import Foundation

/// Saved games in the app's iCloud Drive folder (DeCiv › Saves in the Files app).
///
/// Called from a background thread on the game side (`com.unciv.logic.files.CloudSaves`); every
/// method blocks. File access is coordinated, as iCloud Drive requires. When the app has no iCloud
/// container (no entitlement) or the user is signed out, `savesURL` is nil and nothing happens.
enum CloudSaveStore {
    private static let fileManager = FileManager.default

    static var savesURL: URL? {
        guard let container = fileManager.url(forUbiquityContainerIdentifier: nil) else { return nil }
        let folder = container.appendingPathComponent("Documents/Saves", isDirectory: true)
        try? fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    /// Only the current autosave travels; the numbered per-turn copies stay on the device
    static func isMirrored(_ name: String) -> Bool {
        !name.hasPrefix(".") && !name.hasPrefix("Autosave-")
    }

    static func upload(localPath: String, name: String) {
        guard let folder = savesURL else { return }
        copy(from: URL(fileURLWithPath: localPath), to: folder.appendingPathComponent(name), coordinateDestination: true)
    }

    static func delete(name: String) {
        guard let folder = savesURL else { return }
        let url = folder.appendingPathComponent(name)
        var error: NSError?
        NSFileCoordinator().coordinate(writingItemAt: url, options: .forDeleting, error: &error) { url in
            try? fileManager.removeItem(at: url)
        }
    }

    /// Newer cloud files come down, newer local ones go up. @return the number of local files changed
    static func sync(localFolder: String) -> Int {
        guard let folder = savesURL else { return 0 }
        let local = URL(fileURLWithPath: localFolder, isDirectory: true)
        try? fileManager.createDirectory(at: local, withIntermediateDirectories: true)
        var changed = 0
        var cloudNames = Set<String>()

        let keys: [URLResourceKey] = [.contentModificationDateKey, .ubiquitousItemDownloadingStatusKey]
        let items = (try? fileManager.contentsOfDirectory(at: folder, includingPropertiesForKeys: keys)) ?? []
        for item in items {
            var name = item.lastPathComponent
            // Not downloaded yet: iCloud keeps a ".<name>.icloud" placeholder; ask for the file, next sync takes it
            if name.hasPrefix("."), name.hasSuffix(".icloud") {
                name = String(name.dropFirst().dropLast(".icloud".count))
                cloudNames.insert(name)
                try? fileManager.startDownloadingUbiquitousItem(at: folder.appendingPathComponent(name))
                continue
            }
            guard isMirrored(name) else { continue }
            cloudNames.insert(name)
            let status = try? item.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey]).ubiquitousItemDownloadingStatus
            if status != nil && status != .current && status != .downloaded {
                try? fileManager.startDownloadingUbiquitousItem(at: item)
                continue
            }
            let target = local.appendingPathComponent(name)
            if isNewer(item, than: target) {
                copy(from: item, to: target, coordinateDestination: false)
                changed += 1
            }
        }

        // Local saves the cloud doesn't have yet, or has older
        let localItems = (try? fileManager.contentsOfDirectory(at: local, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        for item in localItems where isMirrored(item.lastPathComponent) {
            let target = folder.appendingPathComponent(item.lastPathComponent)
            if !cloudNames.contains(item.lastPathComponent) || isNewer(item, than: target) {
                copy(from: item, to: target, coordinateDestination: true)
            }
        }
        return changed
    }

    private static func modified(_ url: URL) -> Date? {
        try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate
    }

    /// A second of slack: copies keep their date, but file systems round differently
    private static func isNewer(_ source: URL, than target: URL) -> Bool {
        guard let sourceDate = modified(source) else { return false }
        guard fileManager.fileExists(atPath: target.path), let targetDate = modified(target) else { return true }
        return sourceDate.timeIntervalSince(targetDate) > 1
    }

    /// Copies keeping the modification date, so the next sync sees both sides as equal
    private static func copy(from source: URL, to target: URL, coordinateDestination: Bool) {
        let date = modified(source)
        var error: NSError?
        let coordinator = NSFileCoordinator()
        if coordinateDestination {
            coordinator.coordinate(readingItemAt: source, options: [], writingItemAt: target, options: .forReplacing, error: &error) { from, to in
                replace(from: from, to: to, date: date)
            }
        } else {
            coordinator.coordinate(readingItemAt: source, options: [], error: &error) { from in
                replace(from: from, to: target, date: date)
            }
        }
        if let error { NSLog("[DecivNativeUI] iCloud copy of %@ failed: %@", source.lastPathComponent, error.localizedDescription) }
    }

    private static func replace(from: URL, to: URL, date: Date?) {
        do {
            let data = try Data(contentsOf: from)
            try data.write(to: to, options: .atomic)
            if let date { try? fileManager.setAttributes([.modificationDate: date], ofItemAtPath: to.path) }
        } catch {
            NSLog("[DecivNativeUI] iCloud copy to %@ failed: %@", to.lastPathComponent, error.localizedDescription)
        }
    }
}
