package com.linroid.viewit.data.scanner

import com.linroid.viewit.data.model.Image
import com.linroid.viewit.data.model.ImageType
import com.linroid.viewit.utils.ImageMIME
import rx.Observable
import rx.Subscriber
import rx.schedulers.Schedulers
import timber.log.Timber
import java.io.File

/**
 * @author linroid <linroid@gmail.com>
 * @since 07/01/2017
 */
object SdcardImageScanner : ImageScanner() {
    override fun scan(packageName: String, dirs: List<File>): Observable<Image> {
        return Observable.create<Image> { subscriber ->
            try {
                dirs.forEach {
                    searchImage(packageName, it, subscriber)
                }
            } catch (error: Exception) {
                Timber.e(error, "error occur during search image...")
                return@create
            }
            subscriber.onCompleted()
        }.subscribeOn(Schedulers.io())
    }

    fun searchImage(packageName: String, file: File, subscriber: Subscriber<in Image>) {
        if (!file.exists()) {
            return;
        }
        if (file.isFile) {
            val type = ImageMIME.getImageType(file)
            if (type != ImageType.UNKNOWN) {
                val image = Image(file.absolutePath, file.length(), type)
                subscriber.onNext(image);
            }
        } else if (file.isDirectory) {
            file.listFiles()?.forEach {
                searchImage(packageName, it, subscriber)
            }
        }
    }
}