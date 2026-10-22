package com.dominikgold.tonka.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

class IosSecretStore : SecretStore {

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        memScoped {
            val query = keychainQuery(key, returnData = true)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            CFRelease(query)
            if (status != errSecSuccess) return@withContext null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@withContext null
            data.toByteArray().decodeToString()
        }
    }

    override suspend fun set(key: String, value: String?) = withContext(Dispatchers.IO) {
        val deleteQuery = keychainQuery(key, returnData = false)
        SecItemDelete(deleteQuery)
        CFRelease(deleteQuery)
        if (value == null) return@withContext
        val addQuery = keychainQuery(key, returnData = false)
        val data = value.encodeToByteArray().toNSData()
        CFDictionaryAddValue(addQuery, kSecValueData, CFBridgingRetain(data))
        SecItemAdd(addQuery, null)
        CFRelease(addQuery)
        Unit
    }

    private fun keychainQuery(key: String, returnData: Boolean): CFDictionaryRef? {
        val query = CFDictionaryCreateMutable(null, 5, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(key))
        if (returnData) {
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        }
        return query
    }

    private companion object {
        const val SERVICE = "com.dominikgold.tonka"
    }
}

class IosImageProcessor : ImageProcessor {

    override suspend fun toJpeg(
        bytes: ByteArray,
        maxLongEdge: Int,
        quality: Int,
        cropAspectRatio: Float?,
    ): ProcessedImage? = withContext(Dispatchers.Default) {
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return@withContext null
        // UIImage.size already accounts for the EXIF orientation
        val (sourceWidth, sourceHeight) = image.size.toPair()
        if (sourceWidth <= 0 || sourceHeight <= 0) return@withContext null
        val crop = centerCrop(sourceWidth, sourceHeight, cropAspectRatio)
        val (width, height) = scaledSize(crop.width, crop.height, maxLongEdge)
        val scale = width.toDouble() / crop.width

        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0)
        image.drawInRect(
            CGRectMake(
                -crop.left * scale,
                -crop.top * scale,
                sourceWidth * scale,
                sourceHeight * scale,
            ),
        )
        val rendered = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        val jpeg = rendered?.let { UIImageJPEGRepresentation(it, quality / 100.0) } ?: return@withContext null
        ProcessedImage(jpegBytes = jpeg.toByteArray(), width = width, height = height)
    }

    private fun CValue<CGSize>.toPair(): Pair<Int, Int> = useContents { width.toInt() to height.toInt() }
}

@OptIn(BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), this@toByteArray.bytes, length) }
    }
}
