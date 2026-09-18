package com.qingyu.hermescompanion.data
import com.qingyu.hermescompanion.update.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.security.MessageDigest

class ReleaseUpdatesTest {
    private fun release(version:String="1.10.0", platform:String="macos-arm64"):JSONObject {
        val prefix=if(platform=="windows-x64")"Hermes-Windows-x64" else "Hermes-macOS-arm64"
        val name="$prefix-$version.${if(platform=="windows-x64")"msi" else "dmg"}"
        return JSONObject().put("tag_name","v$version").put("draft",false).put("prerelease",false)
            .put("assets",org.json.JSONArray().put(JSONObject().put("name",name).put("size",3)
                .put("digest","sha256:"+"a".repeat(64)).put("browser_download_url","${ReleaseUpdates.REPO}/releases/download/v$version/$name")))
    }
    @Test fun numericVersionOrderAndNoPrerelease() {
        assertTrue(ReleaseVersion.parse("1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertNull(ReleaseVersion.parse("v1.10.0-beta"))
        assertNull(ReleaseVersion.parse("1.9"))
        assertNull(ReleaseUpdates.parse(release("1.8.9"),"1.9.0","macos-arm64"))
        assertNull(ReleaseUpdates.parse(release().put("prerelease",true),"1.9.0","macos-arm64"))
        assertNull(ReleaseUpdates.parse(release().put("draft",true),"1.9.0","macos-arm64"))
    }
    @Test fun selectCorrectPlatformAndRequireDigest() {
        assertTrue(ReleaseUpdates.parse(release(platform="windows-x64"),"1.9.0","windows-x64")!!.name.endsWith(".msi"))
        val bad=release();bad.getJSONArray("assets").getJSONObject(0).remove("digest")
        assertThrows(IllegalArgumentException::class.java){ReleaseUpdates.parse(bad,"1.9.0","macos-arm64")}
    }
    @Test fun rejectForeignReleaseUrls() {
        val bad=release();bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://evil.example/install.dmg")
        assertThrows(IllegalArgumentException::class.java){ReleaseUpdates.parse(bad,"1.9.0","macos-arm64")}
    }
    @Test fun truncatedAndTamperedDownloadsNeverVerify() {
        val file=Files.createTempFile("hermes-update-test", ".bin").toFile()
        try {
            file.writeText("abc")
            val digest=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
            val r=ReleaseUpdates.parse(release(),"1.9.0","macos-arm64")!!.copy(sha256=digest)
            assertTrue(ReleaseUpdates.verify(file,r))
            file.writeText("ab");assertFalse(ReleaseUpdates.verify(file,r))
            file.writeText("abd");assertFalse(ReleaseUpdates.verify(file,r))
        }finally{file.delete()}
    }
}
