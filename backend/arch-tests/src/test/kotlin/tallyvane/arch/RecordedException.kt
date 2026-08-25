package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import java.io.File

internal data class RecordedException(val rule: String, val reason: String, val adr: String, val location: String)

internal fun KoFileDeclaration.isExempt(rule: String): Boolean = recordedExceptionOrNull()?.rule == rule

internal fun KoContainingFileProvider.isExempt(rule: String): Boolean {
    val onSelf = (this as? KoAnnotationProvider)?.recordedExceptionOrNull()
    if (onSelf?.rule == rule) {
        return true
    }
    return containingFile.recordedExceptionOrNull()?.rule == rule
}

@JvmName("withoutFileException")
internal fun List<KoFileDeclaration>.withoutException(rule: String): List<KoFileDeclaration> =
    filterNot { it.isExempt(rule) }

@JvmName("withoutDeclException")
internal fun <T : KoContainingFileProvider> List<T>.withoutException(rule: String): List<T> =
    filterNot { it.isExempt(rule) }

internal fun KoAnnotationProvider.recordedExceptionOrNull(): RecordedException? {
    val annotation =
        annotations.firstOrNull { item ->
            item.name == "ArchitectureException" ||
                item.fullyQualifiedName == "tallyvane.platform.kernel.ArchitectureException"
        } ?: return null

    fun argument(name: String, index: Int): String {
        val named = annotation.arguments.firstOrNull { it.name == name }?.value
        val positional = annotation.arguments.getOrNull(index)?.value
        return (named ?: positional).orEmpty().trim().trim('"', '\'')
    }
    val location = (this as? KoLocationProvider)?.location ?: annotation.location
    return RecordedException(
        rule = argument("rule", 0),
        reason = argument("reason", 1),
        adr = argument("adr", 2),
        location = location,
    )
}

internal fun KoScope.recordedExceptions(): List<RecordedException> {
    val fromFiles = files.mapNotNull { it.recordedExceptionOrNull() }
    val fromTypes =
        classesAndInterfacesAndObjects(includeNested = true)
            .mapNotNull { it.recordedExceptionOrNull() }
    val fromMembers =
        (functions(includeNested = true) + properties(includeNested = true))
            .mapNotNull { it.recordedExceptionOrNull() }
    return (fromFiles + fromTypes + fromMembers).distinctBy { it.location + it.rule }
}

internal fun adrFileExists(adr: String): Boolean {
    val dir = File(repoRoot(), "docs/adr")
    if (!dir.isDirectory) {
        return false
    }
    return dir.listFiles().orEmpty().any { file ->
        file.name == "$adr.md" || (file.name.startsWith("$adr-") && file.name.endsWith(".md"))
    }
}

internal fun KoFileDeclaration.where(): String = path

internal fun KoLocationProvider.where(): String = location
