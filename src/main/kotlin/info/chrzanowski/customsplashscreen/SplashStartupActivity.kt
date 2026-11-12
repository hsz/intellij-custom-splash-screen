package info.chrzanowski.customsplashscreen

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class SplashStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        service<SplashScreenService>().scheduleRefresh()
    }
}