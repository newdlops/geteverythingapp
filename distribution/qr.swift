import AppKit
import CoreImage
import Foundation

guard CommandLine.arguments.count == 3 else { fatalError("Usage: swift qr.swift URL OUTPUT.png") }
let url = CommandLine.arguments[1]
let filter = CIFilter(name: "CIQRCodeGenerator")!
filter.setValue(Data(url.utf8), forKey: "inputMessage")
filter.setValue("M", forKey: "inputCorrectionLevel")
let qr = filter.outputImage!
let background = CIImage(color: CIColor.white).cropped(to: qr.extent.insetBy(dx: -4, dy: -4))
let image = qr.composited(over: background).transformed(by: CGAffineTransform(scaleX: 8, y: 8))
let context = CIContext()
let bitmap = NSBitmapImageRep(cgImage: context.createCGImage(image, from: image.extent)!)
try bitmap.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[2]))
let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: context, options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])!
let decoded = detector.features(in: image).compactMap { ($0 as? CIQRCodeFeature)?.messageString }
guard decoded == [url] else { fatalError("QR verification failed") }
print("QR generated and decoded: \(url)")
