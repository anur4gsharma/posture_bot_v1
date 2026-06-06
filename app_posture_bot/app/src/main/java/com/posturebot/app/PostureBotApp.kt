package com.posturebot.app

import android.app.Application
import com.posturebot.app.data.db.PostureDatabase
import com.posturebot.app.data.db.PostureDbProvider

class PostureBotApp : Application() {
    val database: PostureDatabase by lazy { PostureDbProvider.create(this) }
}
