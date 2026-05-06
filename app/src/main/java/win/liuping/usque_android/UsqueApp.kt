package win.liuping.usque_android

import android.app.Application
import win.liuping.usque_android.nativebridge.NativeLogger

class UsqueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        mobile.Mobile.setLogger(NativeLogger())
    }
}
