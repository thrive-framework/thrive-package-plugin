package com.github.thriveframework.plugin


import com.github.thriveframework.plugin.extension.ThrivePackageExtension
import com.github.thriveframework.plugin.extension.ThrivePackageSpec
import com.github.thriveframework.plugin.task.CompilePackage
import com.github.thriveframework.plugin.task.PackageJar
import com.github.thriveframework.plugin.task.WriteDockerCompose
import com.github.thriveframework.plugin.task.WritePackage
import com.github.thriveframework.plugin.task.WritePackageProperties
import com.github.thriveframework.plugin.task.WritePackageProviderConfiguration
import com.github.thriveframework.plugin.task.WritePackageYaml
import com.github.thriveframework.plugin.utils.PackageFiles
import com.github.thriveframework.utils.plugin.Gradle
import groovy.util.logging.Slf4j
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.util.ConfigureUtil

import javax.inject.Inject

import static com.github.thriveframework.utils.plugin.Projects.applyPlugin
import static com.github.thriveframework.utils.plugin.Projects.createTask

@Slf4j
class ThrivePackagePlugin implements Plugin<Project> {
    private PackageFiles packageFiles
    private ThrivePackageExtension extension
    private Closure pkgSpec
    private final static String taskGroup = "thrive (package)"
    private SoftwareComponentFactory componentFactory

    @Inject
    ThrivePackagePlugin(SoftwareComponentFactory componentFactory) {
        this.componentFactory = componentFactory
    }

    @Override
    void apply(Project target) {
        Gradle.assertVersionAtLeast("5.5")
        prepare(target)
        addPreparePackageTasks(target)
        addCompilePackageTask(target)
        addPackageJarTask(target)
        configurePublishing(target)
        addComposeConfigurations(target)
        addComposeTask(target)
        bindTasks(target)
//        preconfigureForThrivePlugin() //todo
    }

    private void prepare(Project project){
        packageFiles = new PackageFiles(project)
        packageFiles.packageSrc.mkdirs()
        packageFiles.packageClasses.mkdirs()

        extension = project.extensions.create("thrivePackage", ThrivePackageExtension, project)
        pkgSpec = {
            packageGroup = extension.group
            packageName = extension.name
            targetDir = packageFiles.packageResources
        }

        //todo maybe create source set?

        project.configurations {
            thrivePackage
        }

        project.dependencies {
            //fixme jitpack repo may be missing!; besides, it now requires Yaml that is on central
            //require currently used version - how do I do that?
            thrivePackage "com.github.thrive-framework:thrive-package-plugin:0.1.0-SNAPSHOT"
        }

        AdhocComponentWithVariants component = componentFactory.adhoc("thrive")
        component.addVariantsFromConfiguration(project.configurations.thrivePackage, new JavaConfigurationVariantMapping("compile", false))
        project.components.add(component)
    }

    private void addPreparePackageTasks(Project project){
        createTask(
            project,
            "writePackageProperties",
            WritePackageProperties,
            taskGroup,
            ""//todo
        ) {
            pkg pkgSpec
        }

        createTask(
            project,
            "writePackageYaml",
            WritePackageYaml,
            taskGroup,
            ""//todo
        ) {
            pkg pkgSpec
            composition = project.provider { extension.composition }
        }

        createTask(
            project,
            "preparePackageDir",
            DefaultTask,
            taskGroup,
            "" //todo
        ) {}

        project.tasks.create(
            name: "writePackageSrc",
            type: WritePackage,
            group: taskGroup,
            description: ""//todo
        ) {
            packageGroup = extension.group
            packageName = extension.name
            composition = project.provider { extension.composition }
            targetDir = packageFiles.packageSrc
        }

        project.tasks.create(
            name: "writePackageServiceProviderDescriptor",
            type: WritePackageProviderConfiguration,
            group: taskGroup,
            description: ""//todo
        ) {
            packageGroup = extension.group
            packageName = extension.name
            composition = project.provider { extension.composition }
            targetDir = packageFiles.packageResources
        }

        createTask(
            project,
            "preparePackage",
            DefaultTask,
            taskGroup,
            "" //todo
        ) {}
    }

    private void addCompilePackageTask(Project project){
        def pf = packageFiles
        //todo start using util method
        project.tasks.create(
            name: "compilePackage",
            type: CompilePackage,
            group: taskGroup,
            description: "", //todo
            constructorArgs: [project.tasks.writePackageSrc, project.configurations.thrivePackage]
        ) {
            packageFilesHelper = pf

        }
    }

    private void addPackageJarTask(Project project){
        project.tasks.create(
            name: "packageJar",
            type: PackageJar,
            group: taskGroup,
            description: "",//todo
            constructorArgs: [project.name, packageFiles]
        )
    }

    private void bindTasks(Project project){
        project.preparePackageDir.dependsOn project.writePackageProperties
        project.preparePackageDir.dependsOn project.writePackageYaml

        project.preparePackage.dependsOn project.preparePackageDir
        project.preparePackage.dependsOn project.writePackageSrc
        project.preparePackage.dependsOn project.writePackageServiceProviderDescriptor

        project.compilePackage.dependsOn project.preparePackage
        project.packageJar.dependsOn project.compilePackage
        project.packageJar.dependsOn project.compilePackage

        project.writeDockerCompose.dependsOn project.preparePackageDir

        project.tasks.findByName("build")?.dependsOn project.packageJar
        project.tasks.findByName("build")?.dependsOn project.writeDockerCompose

        project.tasks.findByName("clean")?.doLast {
            packageFiles.root.deleteDir()
            project.file("docker-compose.yml").delete()
        }
    }

    private void configurePublishing(Project project){
        applyPlugin(project, "maven-publish")
        project.publishing.publications.create("thrivePackage", MavenPublication){
            artifactId = "${project.name}-package"
            from project.components.thrive
            artifact project.packageJar
        }
        project.publishThrivePackagePublicationToMavenLocal.dependsOn project.packageJar
    }

    private void addComposeConfigurations(Project project){
        project.configurations.create("generatorExec")
        project.configurations.create("includePackage")

        project.dependencies {
            generatorExec "com.github.thrive-framework.thrive-package-plugin:generator:0.1.0-SNAPSHOT"
        }
    }

    private void addComposeTask(Project project){
        project.tasks.create(
            name: "writeDockerCompose",
            type: WriteDockerCompose,
            group: "thrive (docker)",
            description: "", //todo
            constructorArgs: [project]
        ) {
            execConfigName = "generatorExec"
            packageConfigName = "includePackage"
            packageDirs = [
                project.tasks.writePackageYaml.pkg.packageDir.get().asFile.absolutePath
            ]
            targetDir = project.projectDir
        }
    }
}
