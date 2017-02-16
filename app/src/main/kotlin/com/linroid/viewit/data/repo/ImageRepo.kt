package com.linroid.viewit.data.repo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.support.annotation.IntDef
import com.linroid.rxshell.RxShell
import com.linroid.viewit.App
import com.linroid.viewit.R
import com.linroid.viewit.data.model.Image
import com.linroid.viewit.data.model.ImageTree
import com.linroid.viewit.data.repo.cloud.CloudPathRepo
import com.linroid.viewit.data.repo.local.PathRepo
import com.linroid.viewit.data.scanner.ImageScanner
import com.linroid.viewit.ioc.quailifer.Root
import com.linroid.viewit.utils.*
import com.linroid.viewit.utils.pref.LongPreference
import rx.Observable
import rx.lang.kotlin.BehaviorSubject
import rx.lang.kotlin.PublishSubject
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Named

/**
 * @author linroid <linroid@gmail.com>
 * @since 08/01/2017
 */
class ImageRepo(val context: Context, val appInfo: ApplicationInfo) {
    companion object {
        const val UPDATE_EVENT = 0x1L
        const val REMOVE_EVENT = 0x2L
        const val INSERT_EVENT = 0x3L

        @IntDef(UPDATE_EVENT, REMOVE_EVENT, INSERT_EVENT)
        annotation class ImageEventType

        const val SORT_BY_PATH = 0x1L
        const val SORT_BY_SIZE = 0x2L
        const val SORT_BY_TIME = 0x3L

        @IntDef(SORT_BY_PATH, SORT_BY_SIZE, SORT_BY_TIME)
        annotation class ImageSortType
    }

    @Inject
    lateinit var packageManager: PackageManager
    @field:[Inject Root]
    lateinit var rootShell: RxShell
    @Inject
    lateinit var imageScanner: ImageScanner
    @Inject
    lateinit var localPathRepo: PathRepo
    @Inject
    lateinit var cloudPathRepo: CloudPathRepo

    @field:[Inject Named(PREF_SORT_TYPE)]
    lateinit var sortTypePref: LongPreference

    @field:[Inject Named(PREF_FILTER_SIZE)]
    lateinit var filterSizePref: LongPreference

    private val eventBus = PublishSubject<ImageEvent>()
    private val treeBuilder = BehaviorSubject<ImageTree>()
    private val cacheDir: File = File(context.cacheDir, "mounts")
    val images = ArrayList<Image>()

    var viewerHolderImages: List<Image>? = null

    init {
        App.graph.inject(this)
        createTreeBuilder()
    }

    fun scan(): Observable<Image> {
        // 扫描外部数据
        Timber.d("scan images for ${appInfo.packageName}")
        val externalData: File = context.externalCacheDir.parentFile.parentFile
        var observable: Observable<Image> = imageScanner.scan(appInfo.packageName, File(externalData, appInfo.packageName))

        // 扫描内部数据
        if (RootUtils.isRootAvailable()) {
            val packInfo: PackageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
            val dataDir = packInfo.applicationInfo.dataDir
            observable = imageScanner.scan(appInfo.packageName, File(dataDir))
                    .concatWith(observable)
        }

        // 扫描本地保存的路径
        val localPathScanner = localPathRepo.list(appInfo)
                .map { list -> list.map { item -> File(PathUtils.formatToDevice(item.path, appInfo)) } }
                .onErrorReturn { emptyList() }
                .flatMap { list -> imageScanner.scan(appInfo.packageName, list) }

        // 扫描云端的路径
        val cloudPathScanner = cloudPathRepo.list(appInfo)
                .map { list -> list.map { item -> File(PathUtils.formatToDevice(item.path, appInfo)) } }
                .onErrorReturn { emptyList() }
                .flatMap { list -> imageScanner.scan(appInfo.packageName, list) }

        observable = observable.concatWith(localPathScanner).concatWith(cloudPathScanner)
        val scanned: MutableList<Image> = ArrayList()
        return observable
                .doOnNext {
                    scanned.add(it)
                }
                .distinct()
                .doOnCompleted {
                    Timber.d("doOnCompleted")
                    images.clear()
                    images.addAll(scanned)
                    eventBus.onNext(ImageEvent(UPDATE_EVENT, 0, images.size, images))
                }
    }

    fun registerImageEvent(): PublishSubject<ImageEvent> {
        return eventBus
    }

    fun mountImage(image: Image): Observable<Image> {
        val packageCacheDir: File = File(cacheDir, appInfo.packageName)
        val packInfo: PackageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
        val dataDir: String = packInfo.applicationInfo.dataDir
        val relativePath: String = image.source.absolutePath.substringAfter(dataDir)

        val cacheFile = File(packageCacheDir, relativePath)
        image.mountFile = cacheFile
        if (cacheFile.exists()) {
            return Observable.just(image)
        }
        val targetDir = cacheFile.parentFile
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        if (!cacheFile.exists()) {
            cacheFile.createNewFile()
        }
        return rootShell.copyFile(image.path, cacheFile.absolutePath)
//                .flatMap { RxShell.instance().chown(cacheFile.absolutePath, uid, uid) }
                .map { image }.doOnError { cacheFile }
    }

    fun saveImage(image: Image, appInfo: ApplicationInfo): Observable<File> {
        return Observable.just(image)
                .flatMap { if (it.file() == null) mountImage(image) else Observable.just(image) }
                .flatMap {
                    if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                        throw IllegalStateException(context.getString(R.string.msg_save_image_failed_without_sdcard));
                    }
                    val targetName = "${packageManager.getApplicationLabel(appInfo)}_${System.currentTimeMillis()}.${image.postfix()}"
                    val pictureDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val saveDirectory = File(pictureDirectory, context.getString(R.string.app_name));
                    val desFile: File;
                    if (!saveDirectory.exists()) {
                        saveDirectory.mkdirs();
                    }
                    desFile = File(saveDirectory, targetName);
                    if (!desFile.exists()) {
                        desFile.createNewFile();
                    }
                    FileUtils.copyFile(image.file()!!, desFile);
                    return@flatMap Observable.just(desFile)
                }.subscribeOn(Schedulers.io())
    }

    fun deleteImage(image: Image, appInfo: ApplicationInfo): Observable<Boolean> {
        return Observable.just(image.path)
                .flatMap { path ->
                    if (RootUtils.isRootFile(path)) {
                        return@flatMap rootShell.deleteFile(path)
                    } else {
                        File(image.path).delete()
                        return@flatMap Observable.just(true)
                    }
                }
                .subscribeOn(Schedulers.io())
                .doOnNext {
                    val position = images.indexOf(image)
                    images.remove(image)
                    eventBus.onNext(ImageEvent(REMOVE_EVENT, position, 1, arrayListOf(image)))
                }
    }

    fun registerTreeBuilder(): Observable<ImageTree> {
        return treeBuilder
    }

    fun getImageTree(): ImageTree? = treeBuilder.value

    private fun createTreeBuilder() {
        eventBus.observeOn(Schedulers.computation()).subscribe {
            Timber.d("should update treeBuilder")
            when (it.type) {
                UPDATE_EVENT -> {
                    val map = HashMap<String, ImageTree>()
                    val rootTree = ImageTree(File.separator)
                    images.forEach {
                        val dir = it.source.parent
                        var tree = map[dir]
                        if (tree == null) {
                            tree = ImageTree(it.source.parent)
                            map[dir] = tree
                        }
                        tree.images.add(it)
                    }
                    map.forEach { key, imageTree ->
                        rootTree.add(imageTree)
                    }
                    treeBuilder.onNext(rootTree)
                }
                INSERT_EVENT -> {
                    if (treeBuilder.hasValue()) {
                        val value = treeBuilder.value
                        it.effectedImages.forEach { image ->
                            value.insertImage(image)
                        }
                        treeBuilder.onNext(value)
                    }
                }
                REMOVE_EVENT -> {
                    if (treeBuilder.hasValue()) {
                        val value = treeBuilder.value
                        it.effectedImages.forEach { image ->
                            value.removeImage(image)
                        }
                        treeBuilder.onNext(value)
                    }
                }
            }
        }
    }

    data class ImageEvent(@ImageEventType val type: Long, val position: Int, val effectCount: Int, val effectedImages: List<Image>)

}
