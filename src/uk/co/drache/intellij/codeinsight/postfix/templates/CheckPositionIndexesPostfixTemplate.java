package uk.co.drache.intellij.codeinsight.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.util.Aliases;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE;
import static com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils.isArray;
import static com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils.isIterable;
import static com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils.isNumber;
import static com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils.showErrorHint;
import static uk.co.drache.intellij.codeinsight.postfix.utils.GuavaPostfixTemplatesUtils.isCollection;

/**
 * @author Bob Browning
 */
@Aliases(".cpis")
public class CheckPositionIndexesPostfixTemplate extends PostfixTemplate {

  @NonNls
  private static final String GUAVA_CHECK_POSITION_INDEXES_METHOD =
      "com.google.common.base.Preconditions.checkPositionIndexes";

  public CheckPositionIndexesPostfixTemplate() {
    super("checkpositionindexes",
          "Checks that [start, end) is a valid sub range of a list, string, or array with the specified size.",
          "checkPositionIndexes(start, end, size)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = getTopmostExpression(context);
    return expr != null && (isNumber(expr.getType()) ||
                            isArray(expr.getType()) ||
                            isIterable(expr.getType()));
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null) {
      showErrorHint(context.getProject(), editor);
      return;
    }

    Trinity<String, String, String> bounds = calculateBounds(expr);
    if (bounds == null) {
      showErrorHint(context.getProject(), editor);
      return;
    }

    Project project = context.getProject();
    Document document = editor.getDocument();

    document.deleteString(expr.getTextRange().getStartOffset(), expr.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);

    Template template = manager.createTemplate("", "");
    template.setValue(USE_STATIC_IMPORT_IF_POSSIBLE, true);
    template.setToIndent(true);
    template.setToShortenLongNames(true);
    template.setToReformat(true);

    template.addTextSegment(GUAVA_CHECK_POSITION_INDEXES_METHOD);
    template.addTextSegment("(");
    template.addVariable("start", new TextExpression(bounds.first), true);
    template.addTextSegment(", ");
    template.addVariable("end", new TextExpression(bounds.second), true);
    template.addTextSegment(", ");
    template.addTextSegment(bounds.third);
    template.addTextSegment(");");
    template.addEndVariable();

    manager.startTemplate(editor, template);
  }

  @Nullable
  protected static String getExpressionBound(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (isNumber(type)) {
      return expr.getText();
    } else if (isArray(type)) {
      return expr.getText() + ".length";
    } else if (isCollection(type)) {
      return expr.getText() + ".size()";
    } else if (isIterable(type)) {
      return "com.google.common.collect.Iterables.size(" + expr.getText() + ")";
    }
    return null;
  }

  @Nullable
  protected Trinity<String, String, String> calculateBounds(@NotNull PsiExpression expression) {
    String bound = getExpressionBound(expression);
    return bound != null ? Trinity.create("0", bound, bound) : null;
  }

}
