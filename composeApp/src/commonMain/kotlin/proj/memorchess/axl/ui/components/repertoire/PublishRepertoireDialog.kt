package proj.memorchess.axl.ui.components.repertoire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_cancel
import memorchess.composeapp.generated.resources.library_publish
import memorchess.composeapp.generated.resources.library_publish_description_label
import memorchess.composeapp.generated.resources.library_publish_dialog_title
import memorchess.composeapp.generated.resources.library_publish_id_label
import memorchess.composeapp.generated.resources.library_publish_title_label
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.repertoire.RepertoirePublishLimits
import proj.memorchess.axl.ui.components.popup.KineticDialog

/**
 * Publish or update dialog for one repertoire: an editable public id, title and description,
 * prefilled from [initialSlug]/[repertoireName] and always submitted under [side]. Submit is
 * disabled until every field passes the shared [RepertoirePublishLimits] bounds.
 *
 * Fields reset to their initial values every time [visible] turns true, so a dialog reopened after
 * a cancel starts fresh rather than keeping a stale edit.
 */
@Composable
fun PublishRepertoireDialog(
  visible: Boolean,
  repertoireName: String,
  initialSlug: String,
  side: String,
  onSubmit: (slug: String, title: String, description: String, side: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var slug by remember { mutableStateOf(initialSlug) }
  var title by remember { mutableStateOf(repertoireName) }
  var description by remember { mutableStateOf("") }
  LaunchedEffect(visible) {
    if (visible) {
      slug = initialSlug
      title = repertoireName
      description = ""
    }
  }
  val slugProblem = RepertoirePublishLimits.idProblem(slug)
  val titleTooLong = title.length > RepertoirePublishLimits.MAX_TITLE_LENGTH
  val descriptionTooLong = description.length > RepertoirePublishLimits.MAX_DESCRIPTION_LENGTH
  val canSubmit = slugProblem == null && !titleTooLong && !descriptionTooLong

  KineticDialog(
    visible = visible,
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("publishRepertoireDialog"),
    buttons = {
      TextButton(
        modifier = Modifier.testTag("publishRepertoireDialogCancelButton"),
        onClick = onDismiss,
      ) {
        Text(stringResource(Res.string.dialog_cancel))
      }
      TextButton(
        modifier = Modifier.testTag("publishRepertoireDialogSubmitButton"),
        enabled = canSubmit,
        onClick = { onSubmit(slug, title, description, side) },
      ) {
        Text(stringResource(Res.string.library_publish))
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(Res.string.library_publish_dialog_title))
      TextField(
        value = slug,
        onValueChange = { slug = it },
        label = { Text(stringResource(Res.string.library_publish_id_label)) },
        isError = slugProblem != null,
        modifier = Modifier.testTag("publishRepertoireDialogSlugField"),
      )
      TextField(
        value = title,
        onValueChange = { title = it },
        label = { Text(stringResource(Res.string.library_publish_title_label)) },
        isError = titleTooLong,
        modifier = Modifier.testTag("publishRepertoireDialogTitleField"),
      )
      TextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(Res.string.library_publish_description_label)) },
        isError = descriptionTooLong,
        modifier = Modifier.testTag("publishRepertoireDialogDescriptionField"),
      )
    }
  }
}
