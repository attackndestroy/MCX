package com.attdes.mcx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.*
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var pathText: TextView
    private var currentTree: Uri? = null
    private var currentDir: DocumentFile? = null

    private val mcExt = setOf("mcpack", "mcaddon", "mcworld")
    private val archiveExt = setOf("zip", "rar", "7z")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!getPreferences(MODE_PRIVATE).getBoolean("welcome", false)) welcome()
        else home()
        intent?.data?.let { uri ->
            if (intent.action == Intent.ACTION_VIEW) handleIncoming(uri)
        }
    }

    private fun base(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 20, 20, 10)
    }

    private fun welcome() {
        val box = base().apply { gravity = Gravity.CENTER; setPadding(36,36,36,36) }
        val title = TextView(this).apply { text="MCX"; textSize=44f; gravity=Gravity.CENTER }
        val sub = TextView(this).apply { text="Minecraft File Opener"; textSize=18f; gravity=Gravity.CENTER }
        val msg = TextView(this).apply {
            text="مرحبا بك في MCX 👋\n\nمتصفح ملفات كامل للمواقع التي يسمح بها Android، مع دعم ملفات Minecraft والأرشيفات.\n\nMCX لا يدخل إلى بيانات Minecraft الداخلية."
            textSize=16f; gravity=Gravity.CENTER; setPadding(0,28,0,20)
        }
        val b=MaterialButton(this).apply { text="ابدأ"; setOnClickListener {
            getPreferences(MODE_PRIVATE).edit().putBoolean("welcome",true).apply(); home()
        }}
        box.addView(title); box.addView(sub); box.addView(msg)
        box.addView(b, LinearLayout.LayoutParams(-1,-2).apply{topMargin=20})
        setContentView(box)
    }

    private fun home() {
        val box=base()
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL}
        val title=TextView(this).apply{text="MCX";textSize=32f}
        val browse=MaterialButton(this).apply{text="📁 فتح مجلد";setOnClickListener{chooseTree()}}
        top.addView(title,LinearLayout.LayoutParams(0,-2,1f)); top.addView(browse)
        pathText=TextView(this).apply{text="اختر مجلدا لبدء التصفح";textSize=14f;setPadding(0,12,0,12)}
        list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{addView(list)}
        // Neutral ad slot: intentionally contains no ad SDK or provider.
        // A future ad network can be inserted here without changing the file browser.
        val adSlot = TextView(this).apply {
            text = "مساحة إعلانية"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        box.addView(top);box.addView(pathText);box.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));box.addView(adSlot)
        setContentView(box)
    }

    private fun chooseTree() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, 20)
    }

    override fun onActivityResult(r:Int,c:Int,d:Intent?) {
        super.onActivityResult(r,c,d)
        if(c!=RESULT_OK || d?.data==null)return
        val uri=d.data!!
        if(r==20){
            try { contentResolver.takePersistableUriPermission(uri,d.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) } catch(_:Exception){}
            currentTree=uri; currentDir=DocumentFile.fromTreeUri(this,uri); render()
        }
    }

    private fun render() {
        val dir=currentDir ?: return
        list.removeAllViews()
        pathText.text=dir.name ?: "مجلد"
        dir.listFiles().sortedWith(compareByDescending<DocumentFile>{it.isDirectory}.thenBy{it.name?.lowercase()}).forEach { f ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(8,12,8,12)}
            val icon=TextView(this).apply{text=iconFor(f);textSize=26f;gravity=Gravity.CENTER}
            val name=TextView(this).apply{text=f.name?:"";textSize=16f;setPadding(14,0,8,0)}
            row.addView(icon,LinearLayout.LayoutParams(52,60))
            row.addView(name,LinearLayout.LayoutParams(0,60,1f))
            row.setOnClickListener { if(f.isDirectory){currentDir=f;render()} else fileAction(f) }
            list.addView(row)
        }
        if(dir.parentFile!=null){
            val back=MaterialButton(this).apply{text="⬆ المجلد السابق";setOnClickListener{
                val p=dir.parentFile
                if(p!=null){currentDir=p;render()}
            }}
            list.addView(back,0)
        }
    }

    private fun iconFor(f:DocumentFile):String {
        if(f.isDirectory)return "📁"
        val e=ext(f.name)
        return when(e){
            "mcpack"->"🧩";"mcaddon"->"🛠️";"mcworld"->"🌍"
            "zip"->"🗜️";"rar"->"📦";"7z"->"🗃️"
            "png","jpg","jpeg","webp"->"🖼️"
            else->"📄"
        }
    }

    private fun fileAction(f:DocumentFile) {
        val e=ext(f.name)
        when {
            mcExt.contains(e)->openMinecraft(f.uri)
            archiveExt.contains(e)->archiveDialog(f)
            else->MaterialAlertDialogBuilder(this).setTitle("ملف غير مدعوم")
                .setMessage("MCX يركز على ملفات Minecraft والأرشيفات.").setPositiveButton("حسنا",null).show()
        }
    }

    private fun archiveDialog(f:DocumentFile) {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,10,20,0)}
        val pass=EditText(this).apply{hint="كلمة المرور (اتركها فارغة إن لم توجد)";inputType=0x00000081}
        box.addView(pass)
        MaterialAlertDialogBuilder(this).setTitle("استخراج ${f.name}")
            .setView(box).setNegativeButton("إلغاء",null)
            .setPositiveButton("استخراج"){_,_-> extractAsync(f,pass.text.toString())}.show()
    }

    private fun extractAsync(f:DocumentFile,password:String) {
        val dest=currentDir ?: return
        Toast.makeText(this,"جار استخراج الأرشيف…",Toast.LENGTH_SHORT).show()
        thread {
            try {
                val cache=File(cacheDir,"archive_${System.currentTimeMillis()}.${ext(f.name)}")
                contentResolver.openInputStream(f.uri)!!.use { input -> cache.outputStream().use { input.copyTo(it) } }
                val out=File(cacheDir,"out_${System.currentTimeMillis()}").apply{mkdirs()}
                when(ext(f.name)){
                    "zip"->extractZip(cache,out,password)
                    "7z"->extract7z(cache,out,password)
                    "rar"->extractRar(cache,out,password)
                }
                val folderName=(f.name?:"MCX").substringBeforeLast('.')
                val target=dest.createDirectory(safeName(folderName)) ?: throw IOException("لا يمكن إنشاء مجلد الوجهة")
                copyTreeToDocument(out,target)
                runOnUiThread {
                    Toast.makeText(this,"تم الاستخراج إلى ${target.name}",Toast.LENGTH_LONG).show()
                    render()
                }
                cache.delete();out.deleteRecursively()
            } catch(e:Exception) {
                runOnUiThread {
                    MaterialAlertDialogBuilder(this).setTitle("فشل الاستخراج")
                        .setMessage(e.message ?: "كلمة المرور خاطئة أو الأرشيف غير صالح.")
                        .setPositiveButton("حسنا",null).show()
                }
            }
        }
    }

    private fun extractZip(file:File,out:File,password:String) {
        // Commons Compress handles ZIP including encrypted entries.
        val b=ZipFile.builder().setFile(file)
        if(password.isNotEmpty()) b.setPassword(password.toCharArray())
        b.get().use { z ->
            val en=z.entries
            while(en.hasMoreElements()){
                val e=en.nextElement()
                safeExtract(out,e.name){ os->z.getInputStream(e).use{it.copyTo(os)} }
            }
        }
    }

    private fun extract7z(file:File,out:File,password:String) {
        val b=SevenZFile.builder().setFile(file)
        if(password.isNotEmpty()) b.setPassword(password.toCharArray())
        b.get().use { z ->
            for(e in z.entries){
                if(e.isDirectory) { File(out,e.name).mkdirs(); continue }
                val target=safeFile(out,e.name); target.parentFile?.mkdirs()
                FileOutputStream(target).use { os ->
                    val buf=ByteArray(8192); var n=z.read(buf)
                    while(n>0){os.write(buf,0,n);n=z.read(buf)}
                }
            }
        }
    }

    private fun extractRar(file:File,out:File,password:String) {
        val archive=if(password.isEmpty()) Archive(file) else Archive(file,password)
        archive.use { a ->
            for(h in a.fileHeaders){
                if(h.isDirectory){File(out,h.fileNameString).mkdirs();continue}
                val target=safeFile(out,h.fileNameString);target.parentFile?.mkdirs()
                FileOutputStream(target).use{a.extractFile(h,it)}
            }
        }
    }

    private fun safeExtract(root:File,name:String,write:(OutputStream)->Unit){
        val target=safeFile(root,name)
        target.parentFile?.mkdirs()
        if(name.endsWith("/")){target.mkdirs();return}
        FileOutputStream(target).use(write)
    }

    private fun safeFile(root:File,name:String):File {
        val clean=name.replace('\\','/').trimStart('/')
        val target=File(root,clean)
        val r=root.canonicalPath+File.separator
        if(!target.canonicalPath.startsWith(r)) throw SecurityException("مسار أرشيف غير آمن")
        return target
    }

    private fun copyTreeToDocument(src:File,dst:DocumentFile) {
        src.listFiles().forEach { f ->
            if(f.isDirectory){
                val d=dst.createDirectory(safeName(f.name))
                if(d!=null)copyTreeToDocument(f,d)
            }else{
                val extMime=mime(f.name)
                val d=dst.createFile(extMime,safeName(f.name)) ?: throw IOException("تعذر إنشاء الملف")
                FileInputStream(f).use{input->contentResolver.openOutputStream(d.uri)!!.use{output->input.copyTo(output)}}
            }
        }
    }

    private fun safeName(s:String?):String {
        val n=(s?:"file").replace(Regex("[\\\\/:*?\"<>|]"),"_").trim()
        return if(n.isEmpty())"file" else n
    }
    private fun ext(n:String?):String=n?.substringAfterLast('.', "")?.lowercase()?:""
    private fun mime(n:String?):String=when(ext(n)){
        "mcpack","mcaddon","mcworld","zip","rar","7z"->"application/octet-stream"
        "png"->"image/png";"jpg","jpeg"->"image/jpeg";"webp"->"image/webp"
        else->"application/octet-stream"
    }

    private fun openMinecraft(uri:Uri) {
        val i=Intent(Intent.ACTION_VIEW).apply{
            data=uri;type=contentResolver.getType(uri) ?: "application/octet-stream"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try{startActivity(i)}catch(e:Exception){
            MaterialAlertDialogBuilder(this).setTitle("تعذر فتح Minecraft")
                .setMessage("لم يجد Android تطبيقا مناسبا. تأكد من تثبيت Minecraft.")
                .setPositiveButton("حسنا",null).show()
        }
    }

    private fun handleIncoming(uri:Uri){
        val e=ext(uri.lastPathSegment)
        if(mcExt.contains(e))openMinecraft(uri)
        else Toast.makeText(this,"يمكنك اختيار الأرشيف من داخل MCX لاستخراجه.",Toast.LENGTH_LONG).show()
    }
}
