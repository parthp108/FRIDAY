package com.health.friday.sync.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveFilePageParserTest {
    @Test
    fun parsesFilesWhenJsonFieldOrderVariesAndRetainsNextPageToken() {
        val page = DriveFilePageParser.parse(
            """{
              "nextPageToken":"page-token-2",
              "files":[
                {"name":"batch_abc.friday","modifiedTime":"2026-11-19T04:00:00Z","id":"drive-id-1"},
                {"id":"drive-id-2","name":"friday_key_envelope_123.json"}
              ]
            }"""
        )

        assertEquals(2, page.files.size)
        assertEquals("drive-id-1", page.files[0].id)
        assertEquals("batch_abc.friday", page.files[0].name)
        assertEquals("page-token-2", page.nextPageToken)
        assertNull(page.files[1].modifiedTime)
    }

    @Test
    fun emptyDrivePageProducesEmptyListAndNoToken() {
        val page = DriveFilePageParser.parse("""{"files":[]}""")
        assertEquals(0, page.files.size)
        assertNull(page.nextPageToken)
    }
}
