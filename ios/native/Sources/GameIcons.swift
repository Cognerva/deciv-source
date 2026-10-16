import SwiftUI
import UIKit

/// Reads icons straight out of the game's libGDX texture atlases in the app bundle, so native
/// screens can show the same unit, building, tech and stat icons as the map.
///
/// Atlas text files are indexed once (region name → page and rectangle). Page PNGs are decoded
/// lazily and cropped regions are cached.
final class GameIconStore {
    static let shared = GameIconStore()

    private struct Region {
        let page: String
        let rect: CGRect
        let rotated: Bool
    }

    private var regions: [String: Region] = [:]
    private var pages: [String: CGImage] = [:]
    private let cache = NSCache<NSString, UIImage>()
    private var indexed = false

    func image(_ name: String) -> UIImage? {
        if let cached = cache.object(forKey: name as NSString) { return cached }
        // Loose images such as the app icon previews, by their bundle path
        if name.hasSuffix(".png") {
            guard let path = Bundle.main.resourceURL?.appendingPathComponent(name).path,
                  let image = UIImage(contentsOfFile: path) else { return nil }
            cache.setObject(image, forKey: name as NSString)
            return image
        }
        indexIfNeeded()
        guard let region = regions[name], let page = page(region.page),
              let cropped = page.cropping(to: region.rect) else { return nil }
        var image = UIImage(cgImage: cropped, scale: 2, orientation: .up)
        if region.rotated {
            // libGDX stores rotated regions turned 90° clockwise
            image = UIImage(cgImage: cropped, scale: 2, orientation: .left)
        }
        cache.setObject(image, forKey: name as NSString)
        return image
    }

    private func page(_ file: String) -> CGImage? {
        if let page = pages[file] { return page }
        guard let url = Bundle.main.resourceURL?.appendingPathComponent(file),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { return nil }
        pages[file] = image
        return image
    }

    private func indexIfNeeded() {
        guard !indexed else { return }
        indexed = true
        guard let root = Bundle.main.resourceURL,
              let files = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)
        else { return }
        for file in files where file.pathExtension == "atlas" {
            if let text = try? String(contentsOf: file, encoding: .utf8) { parse(text) }
        }
    }

    /// Supports both the current libGDX format (`bounds:x,y,w,h`) and the legacy one
    /// (`xy:` and `size:` on separate lines).
    private func parse(_ text: String) {
        var page: String?
        var expectPage = true
        var name: String?
        var xy: CGPoint?
        var size: CGSize?
        var bounds: CGRect?
        var rotated = false

        func flush() {
            guard let page, let name else { return }
            let rect = bounds ?? xy.flatMap { origin in size.map { CGRect(origin: origin, size: $0) } }
            if let rect {
                let stored = rotated ? CGRect(x: rect.minX, y: rect.minY, width: rect.height, height: rect.width) : rect
                regions[name] = Region(page: page, rect: stored, rotated: rotated)
            }
        }

        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty {
                flush()
                name = nil
                expectPage = true
                continue
            }
            if expectPage {
                page = line
                expectPage = false
                continue
            }
            if let colon = line.firstIndex(of: ":") {
                let key = line[..<colon].trimmingCharacters(in: .whitespaces)
                let numbers = line[line.index(after: colon)...]
                    .split(separator: ",")
                    .map { $0.trimmingCharacters(in: .whitespaces) }
                let values = numbers.compactMap { Double($0) }
                switch key {
                case "bounds" where values.count == 4:
                    bounds = CGRect(x: values[0], y: values[1], width: values[2], height: values[3])
                case "xy" where values.count == 2:
                    xy = CGPoint(x: values[0], y: values[1])
                case "size" where values.count == 2 && name != nil:
                    size = CGSize(width: values[0], height: values[1])
                case "rotate":
                    rotated = numbers.first == "true" || numbers.first == "90"
                default:
                    break
                }
                continue
            }
            // A line without a colon starts a new region
            flush()
            name = line
            xy = nil
            size = nil
            bounds = nil
            rotated = false
        }
        flush()
    }
}

/// An icon from the game's atlases, falling back to an SF Symbol when the region is missing.
struct GameIcon: View {
    let name: String
    var fallback = "circle.fill"
    /// Tint for white silhouette icons (unit, building, stat icons). Nil keeps original colours.
    var tint: Color? = nil

    var body: some View {
        if let image = GameIconStore.shared.image(name) {
            if let tint {
                Image(uiImage: image)
                    .renderingMode(.template)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: .fit)
                    .foregroundStyle(tint)
            } else {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: .fit)
            }
        } else {
            Image(systemName: Symbols.named(fallback))
                .resizable()
                .aspectRatio(contentMode: .fit)
                .foregroundStyle(tint ?? .primary)
        }
    }
}
