package win.liuping.usque_android.nativebridge

import android.util.Log

class NativeLogger : mobile.Logger {
    override fun log(msg: String) {
        Log.d("UsqueNative", msg)
    }
}
