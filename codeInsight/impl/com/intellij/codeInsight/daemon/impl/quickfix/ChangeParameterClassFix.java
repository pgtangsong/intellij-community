/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ChangeParameterClassFix extends ExtendsListFix {
  private ChangeParameterClassFix(PsiClass aClassToExtend, PsiClassType parameterClass) {
    super(aClassToExtend, parameterClass, true);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.parameter.class.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
        super.isAvailable(project, editor, file)
        && myClass != null
        && myClass.isValid()
        && myClass.getQualifiedName() != null
        && myClassToExtendFrom != null
        && myClassToExtendFrom.isValid()
        && myClassToExtendFrom.getQualifiedName() != null
        && myClass.getManager().isInProject(myClass)
    ;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          invokeImpl();
        }
      }
    );
    final Editor editor1 = CodeInsightUtil.positionCursor(project, myClass.getContainingFile(), myClass);
    if (editor1 == null) return;
    final Collection<CandidateInfo> toImplement = OverrideImplementUtil.getMethodsToOverrideImplement(myClass, true);
    if (!toImplement.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              Collection<PsiMethodMember> members =
                ContainerUtil.map2List(toImplement, new Function<CandidateInfo, PsiMethodMember>() {
                  public PsiMethodMember fun(final CandidateInfo s) {
                    return new PsiMethodMember(s);
                  }
                });
              OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor1, myClass, members, false);
            }
          });
      }
      else {
        //SCR 12599
        editor1.getCaretModel().moveToOffset(myClass.getTextRange().getStartOffset());

        OverrideImplementUtil.chooseAndImplementMethods(project, editor1, myClass);
      }
    }
  }

  public static void registerQuickFixActions(PsiMethodCallExpression methodCall, PsiExpressionList list, HighlightInfo highlightInfo) {
    final JavaResolveResult result = methodCall.getMethodExpression().advancedResolve(false);
    PsiMethod method = (PsiMethod) result.getElement();
    final PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null || method.getParameterList().getParametersCount() != expressions.length) return;
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      PsiParameter parameter = method.getParameterList().getParameters()[i];
      PsiType expressionType = expression.getType();
      PsiType parameterType = substitutor.substitute(parameter.getType());
      if (expressionType == null || expressionType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(expressionType) || expressionType instanceof PsiArrayType ) continue;
      if (parameterType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(parameterType) || parameterType instanceof PsiArrayType ) continue;
      if (parameterType.isAssignableFrom(expressionType)) continue;
      PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
      PsiClass expressionClass = PsiUtil.resolveClassInType(expressionType);
      if (parameterClass == null || expressionClass == null) continue;
      if (parameterClass.isInheritor(expressionClass, true)) continue;
      QuickFixAction.registerQuickFixAction(highlightInfo, new ChangeParameterClassFix(expressionClass, (PsiClassType)parameterType));
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
