package com.xburnsx.toutiebudget.data.modeles

import java.util.Date

abstract class BaseModel {
    abstract var id: String
    abstract var created: Date?
    abstract var updated: Date?
}
