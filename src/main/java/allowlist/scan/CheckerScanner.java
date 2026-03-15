package allowlist.scan;

import allowlist.AllowlistConfig;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.management.openmbean.ArrayType;

/** AST scanner that enforces an allowlist for JDK APIs. */
public final class CheckerScanner extends TreePathScanner<Void, Void> {
  private final Trees trees;
  private final Types types;
  private final CompilationUnitTree cu;
  private final Elements elements;

  private final List<String> violations;
  private final SourcePositions srcPos;
  private final AllowlistConfig config;

  private boolean inVoidMethod = false;

  public CheckerScanner(
      Trees trees,
      Types types,
      Elements elements,
      CompilationUnitTree cu,
      List<String> violations,
      AllowlistConfig config) {
    this.trees = trees;
    this.types = types;
    this.elements = elements;
    this.cu = cu;
    this.violations = violations;
    this.srcPos = trees.getSourcePositions();
    this.config = config;
  }

  @Override
  public Void visitImport(ImportTree node, Void p) {
    if (config.requireWildcardImports()) {
      String q = node.getQualifiedIdentifier().toString();
      boolean isWildcard = q.endsWith(".*");
      if (!isWildcard) {
        addViolation(node, "Non-wildcard import is not allowed: import " + q + ";");
      }
    }

    return super.visitImport(node, p);
  }

  @Override
  public Void visitMethod(MethodTree node, Void p) {
    boolean prevInVoid = inVoidMethod;

    // Determine if this method is void
    boolean isVoid = false;
    Tree rt = node.getReturnType();
    if (rt != null) {
      String rts = rt.toString();
      if (rts.equals("void")) {
        isVoid = true;
      }
    }

    inVoidMethod = isVoid;

    Void out = super.visitMethod(node, p);
    inVoidMethod = prevInVoid;
    return out;
  }

  @Override
  public Void visitReturn(ReturnTree node, Void p) {
    if (config.disallowReturnFromVoid()) {
      if (inVoidMethod && node.getExpression() == null) {
        addViolation(
            node,
            "Return-from-void is not allowed. Use if/else structure instead of early return.");
      }
    }

    return super.visitReturn(node, p);
  }

  @Override
  public Void visitBreak(BreakTree node, Void p) {
    if (config.disallowBreak()) {
      addViolation(node, "break is not allowed.");
    }

    return super.visitBreak(node, p);
  }

  @Override
  public Void visitContinue(ContinueTree node, Void p) {
    if (config.disallowContinue()) {
      addViolation(node, "continue is not allowed.");
    }

    return super.visitContinue(node, p);
  }

  @Override
  public Void visitSwitch(SwitchTree node, Void p) {
    if (config.disallowSwitch()) {
      addViolation(node, "switch is not allowed.");
    }

    return super.visitSwitch(node, p);
  }

  @Override
  public Void visitTry(TryTree node, Void p) {
    if (config.disallowTry()) {
      addViolation(node, "try/catch/finally is not allowed.");
    }

    return super.visitTry(node, p);
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
    Element e = trees.getElement(getCurrentPath());
    if (!(e instanceof ExecutableElement)) {
      return super.visitMethodInvocation(node, p);
    }

    ExecutableElement exe = (ExecutableElement) e;
    String declaringOwner = ownerQualifiedName(exe.getEnclosingElement());
    String methodName = exe.getSimpleName().toString();

    if (!config.shouldEnforceOwner(declaringOwner)) {
      return super.visitMethodInvocation(node, p);
    }

    if (config.isAllowed(declaringOwner, methodName)) {
      return super.visitMethodInvocation(node, p);
    }

    // If this is a member call like recv.method,
    // allow only if the receiver's exact static type is allowlisted
    ExpressionTree select = node.getMethodSelect();
    if (select instanceof MemberSelectTree) {
      MemberSelectTree ms = (MemberSelectTree) select;
      ExpressionTree recvExpr = ms.getExpression();

      TypeMirror recvType = trees.getTypeMirror(new TreePath(getCurrentPath(), recvExpr));
      String recvOwner = erasedQualifiedName(recvType);
      if (!recvOwner.isEmpty() && config.isAllowed(recvOwner, methodName)) {
        return super.visitMethodInvocation(node, p);
      }
    }

    addViolation(node, "Disallowed API usage: " + declaringOwner + "#" + methodName);
    return super.visitMethodInvocation(node, p);
  }

  @Override
  public Void visitNewClass(NewClassTree node, Void p) {
    Element e = trees.getElement(getCurrentPath());
    if (e instanceof ExecutableElement) {
      ExecutableElement ctor = (ExecutableElement) e;

      String owner = ownerQualifiedName(ctor.getEnclosingElement());

      if (!config.shouldEnforceOwner(owner)) {
        return super.visitNewClass(node, p);
      }

      if (!config.isAllowed(owner, "<init>")) {
        addViolation(node, "Disallowed API usage: " + owner + "#<init>");
      }
    }

    return super.visitNewClass(node, p);
  }

  @Override
  public Void visitMemberSelect(MemberSelectTree node, Void p) {
    Element e = trees.getElement(getCurrentPath());
    if (e instanceof VariableElement) {
      VariableElement ve = (VariableElement) e;

      String owner = ownerQualifiedName(ve.getEnclosingElement());
      String name = ve.getSimpleName().toString();

      if (!config.shouldEnforceOwner(owner)) {
        return super.visitMemberSelect(node, p);
      }

      if (!config.isAllowed(owner, name)) {
        addViolation(node, "Disallowed API usage: " + owner + "#" + name);
      }
    }

    return super.visitMemberSelect(node, p);
  }

  @Override
  public Void visitLiteral(LiteralTree node, Void p) {
    if (config.disallowNullLiteral() && node.getKind() == Tree.Kind.NULL_LITERAL) {
      addViolation(node, "Use of null literal is not allowed.");
    }

    return super.visitLiteral(node, p);
  }

  @Override
  public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
    if (!config.disallowEnhancedForloopOverStackOrQueue()) {
      return super.visitEnhancedForLoop(node, p);
    }

    ExpressionTree expr = node.getExpression();
    TypeMirror tm = trees.getTypeMirror(new TreePath(getCurrentPath(), expr));

    // Ignore Arrays
    if (tm instanceof ArrayType) {
      return super.visitEnhancedForLoop(node, p);
    }

    if (isSubtypeOfErased(tm, "java.util.Queue") || isExactlyErased(tm, "java.util.Stack")) {
      addViolation(node, "Enhanced for-loop is not allowed over Queue/Stack.");
    }

    return super.visitEnhancedForLoop(node, p);
  }

  private void addViolation(Tree node, String message) {
    long start = srcPos.getStartPosition(cu, node);
    LineMap lm = cu.getLineMap();

    long line = (start >= 0) ? lm.getLineNumber(start) : -1;
    long col = (start >= 0) ? lm.getColumnNumber(start) : -1;

    String file = (cu.getSourceFile() == null) ? "<unknown>" : cu.getSourceFile().getName();
    violations.add(file + ":" + line + ":" + col + ": " + message);
  }

  private static String ownerQualifiedName(Element enclosing) {
    if (enclosing instanceof TypeElement) {
      return ((TypeElement) enclosing).getQualifiedName().toString();
    }

    // Fallback
    return enclosing.toString();
  }

  private String erasedQualifiedName(TypeMirror tm) {
    if (tm == null) {
      return "";
    }

    TypeMirror er = types.erasure(tm);
    Element el = types.asElement(er);
    if (el instanceof TypeElement) {
      return ((TypeElement) el).getQualifiedName().toString();
    }

    return "";
  }

  private boolean isSubtypeOfErased(TypeMirror tm, String targetQname) {
    if (tm == null) {
      return false;
    }

    TypeElement target = elements.getTypeElement(targetQname);
    if (target == null) {
      return false;
    }

    TypeMirror lhs = types.erasure(tm);
    TypeMirror rhs = types.erasure(target.asType());
    return types.isSubtype(lhs, rhs);
  }

  private boolean isExactlyErased(TypeMirror tm, String targetQname) {
    if (tm == null) {
      return false;
    }

    TypeMirror er = types.erasure(tm);
    Element el = types.asElement(er);
    return (el instanceof TypeElement)
        && ((TypeElement) el).getQualifiedName().contentEquals(targetQname);
  }
}
