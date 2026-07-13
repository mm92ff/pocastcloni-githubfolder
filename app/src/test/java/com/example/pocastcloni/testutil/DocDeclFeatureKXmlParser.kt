package com.example.pocastcloni.testutil

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

class DocDeclFeatureKXmlParser : KXmlParser() {
    override fun setFeature(
        name: String,
        value: Boolean
    ) {
        if (name == XmlPullParser.FEATURE_PROCESS_DOCDECL && !value) return
        super.setFeature(name, value)
    }
}
