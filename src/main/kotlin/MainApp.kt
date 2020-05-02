package org.example

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MainApp : Application() {

    @Throws(Exception::class)
    override fun start(stage: Stage) {
        val root: Parent = FXMLLoader.load(javaClass.getResource("scene.fxml"))
        val scene = Scene(root)
        stage.minWidth = root.minWidth(-1.0)
        stage.minHeight = root.minHeight(-1.0)
        scene.stylesheets.add(javaClass.getResource("styles.css").toExternalForm())
        stage.title = "WebRTC Demo"
        stage.scene = scene
        stage.show()
    }

    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val logger: Logger = LoggerFactory.getLogger(MainApp::class.java)
            logger.info("This is how you configure Log4J with SLF4J")

            launch(MainApp::class.java, *args)
        }
    }
}