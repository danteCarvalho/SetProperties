package setproperties.handlers;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
public class SampleHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow iWorkbenchWindow = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IWorkbenchPage iWorkbenchPage = iWorkbenchWindow.getActivePage();
		IEditorPart iEditorPart = iWorkbenchPage.getActiveEditor();
		try {
			if (iEditorPart != null) {
				IEditorInput iEditorInput = iEditorPart.getEditorInput();
				ITextEditor iTextEditor = (ITextEditor) iEditorPart;
				IDocument document = iTextEditor.getDocumentProvider().getDocument(iTextEditor.getEditorInput());
				FileEditorInput fileEditorInput = (FileEditorInput) iEditorInput;
				IFile iFile = fileEditorInput.getFile();
				IProject project = iFile.getProject();
				IJavaProject javaProject = JavaCore.create(project);
				URLClassLoader classLoader = projectClassLoader(javaProject);
				IJavaElement element = JavaUI.getEditorInputJavaElement(iEditorInput);
				ICompilationUnit iCompilationUnit = (ICompilationUnit) element;
				iCompilationUnit.becomeWorkingCopy(null);
				ITextSelection iTextSelection = (ITextSelection) iTextEditor.getSelectionProvider().getSelection();
				ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
				parser.setResolveBindings(true);
				parser.setBindingsRecovery(true);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				Map<String, String> options = JavaCore.getOptions();
				parser.setCompilerOptions(options);
				parser.setSource(iCompilationUnit);
				CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
				// Use selection length so finder points to the selected name (method or variable)
				NodeFinder finder = new NodeFinder(compilationUnit, iTextSelection.getOffset(), iTextSelection.getLength());
				ASTNode astNode = finder.getCoveringNode();
				// Try to find a MethodInvocation in or above the selected node
				ASTNode n = astNode;
				MethodInvocation methodInvocation = null;
				while (n != null) {
					if (n instanceof MethodInvocation) {
						methodInvocation = (MethodInvocation) n;
						break;
					}
					n = n.getParent();
				}
				if (methodInvocation != null) {
					return setVariables(document, classLoader, methodInvocation);
				}
				// fallback: original behavior (selected expression -> setX() for fields)
				setProperties(document, javaProject, classLoader, iTextSelection, astNode);
			}
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	private void setProperties(IDocument document, IJavaProject javaProject, URLClassLoader classLoader, ITextSelection iTextSelection, ASTNode astNode) throws JavaModelException, ClassNotFoundException, BadLocationException {
		Expression expression = (Expression) astNode;
		ITypeBinding iTypeBinding = expression.resolveTypeBinding();
		IType iType = javaProject.findType(iTypeBinding.getQualifiedName());
		Class<?> clazz = classLoader.loadClass(iType.getFullyQualifiedName());
		Field[] fields = clazz.getDeclaredFields();
		StringBuilder textos = new StringBuilder();
		for (Field field : fields) {
			String novaLinha = iTextSelection.getText() + ".set" + StringUtils.capitalize(field.getName()) + "(" + texto(field) + ");\n";
			textos.append(novaLinha);
		}
		int line = document.getLineOfOffset(iTextSelection.getOffset());
		IRegion proximaLinha = document.getLineInformation(line + 1);
		document.replace(proximaLinha.getOffset(), 0, textos.toString());
	}

	private Object setVariables(IDocument document, URLClassLoader classLoader, MethodInvocation methodInvocation) throws ClassNotFoundException, BadLocationException {
		// Handle method invocation: create parameter variables and replace args with variables
		IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
		ITypeBinding[] paramTypes = methodBinding.getParameterTypes();
		String[] paramNames = methodBinding.getParameterNames();
		IJavaElement javaElem = methodBinding.getJavaElement();
		Method reflectMethod = null;
		IMethod iMethodElem = (IMethod) javaElem;
		// Try to obtain a java.lang.reflect.Method from the IMethod using the project classloader
		IType declaring = (IType) iMethodElem.getParent();
		String declaringName = declaring.getFullyQualifiedName('.');
		Class<?> declaringClass = classLoader.loadClass(declaringName);
		// Build parameter Class[] from ITypeBinding and try to get the exact reflective Method
		for (Method m : declaringClass.getDeclaredMethods()) {
			if (m.getName().equals(iMethodElem.getElementName()) && m.getParameterCount() == paramTypes.length) {
				reflectMethod = m;
				break;
			}
		}
		if (reflectMethod == null) {
			for (Method m : declaringClass.getMethods()) {
				if (m.getName().equals(iMethodElem.getElementName()) && m.getParameterCount() == paramTypes.length) {
					reflectMethod = m;
					break;
				}
			}
		}
		// Build declarations and argument replacement
		StringBuilder declarations = new StringBuilder();
		StringBuilder argsList = new StringBuilder();
		Class<?>[] rawTypes = null;
		Type[] reflectGenericTypes = null;
		reflectGenericTypes = reflectMethod.getGenericParameterTypes();
		rawTypes = reflectMethod.getParameterTypes();
		for (int i = 0; i < paramTypes.length; i++) {
			ITypeBinding tb = paramTypes[i];
			String paramName = paramNames[i];
			Type paramReflectType = reflectGenericTypes[i];
			Class<?> rawClass = rawTypes[i];
			String initializer = texto(rawClass, paramReflectType);
			String typeDeclaration = tb.getName();
			declarations.append(typeDeclaration).append(" ").append(paramName).append(" = ").append(initializer).append(";\n");
			if (i > 0)
				argsList.append(", ");
			argsList.append(paramName);
		}
		// Prepare invocation text and result handling
		// Determine the enclosing Statement early so we can build the full-statement replacement
		ASTNode parent = methodInvocation;
		while (parent != null && !(parent instanceof Statement)) {
			parent = parent.getParent();
		}
		String qualifier = "";
		if (methodInvocation.getExpression() != null) {
			qualifier = methodInvocation.getExpression().toString() + ".";
		}
		// Build the invocation replacement (methodName(arg1, arg2...))
		String invocationReplacement = qualifier + methodInvocation.getName().getIdentifier() + "(" + argsList.toString() + ")";
		// Try to build callText as the whole enclosing statement text with the invocation replaced
		String callText = invocationReplacement;
		String stmtText = document.get(parent.getStartPosition(), parent.getLength());
		int relInvOffset = methodInvocation.getStartPosition() - parent.getStartPosition();
		int relInvLen = methodInvocation.getLength();
		if (relInvOffset >= 0 && relInvOffset + relInvLen <= stmtText.length()) {
			String before = stmtText.substring(0, relInvOffset);
			String after = stmtText.substring(relInvOffset + relInvLen);
			callText = before + invocationReplacement + after;
		}
		// Replace the entire enclosing statement with declarations + the modified statement in one atomic edit
		String declarationsIndented = declarations.toString();
		String replacement = declarationsIndented + callText + "\n";
		document.replace(parent.getStartPosition(), parent.getLength(), replacement);
		return null;
	}

	private URLClassLoader projectClassLoader(IJavaProject javaProject) throws CoreException, MalformedURLException {
		String[] classPathEntries = JavaRuntime.computeDefaultRuntimeClassPath(javaProject);
		List<URL> urlList = new ArrayList<>();
		for (String string : classPathEntries) {
			IPath path = new Path(string);
			URL url = path.toFile().toURI().toURL();
			urlList.add(url);
		}
		URL[] urls = urlList.toArray(new URL[urlList.size()]);
		ClassLoader parentClassLoader = javaProject.getClass().getClassLoader();
		return new URLClassLoader(urls, parentClassLoader);
	}

	private static String texto(Field field) {
		Class<?> classe = field.getType();
		Type genericType = field.getGenericType();
		return texto(classe, genericType);
	}

	private static String texto(Class<?> classe, Type type) {
		if (classe.isArray()) {
			if (classe == type) {
				return "new " + classe.getSimpleName() + "{" + texto(classe.getComponentType(), null) + "}";
			}
			else {
				Type proximoTipo = proximoTipo2(type);
				return "new " + classe.getSimpleName() + "{" + texto(classe.getComponentType(), proximoTipo) + "}";
			}
		}
		else if (Map.class.isAssignableFrom(classe)) {
			if (classe == type) {
				return "Map.of(1,1)";
			}
			else {
				Type proximoTipo = proximoTipo(type, 0);
				Class<?> proximaClasse = proximaClasse(proximoTipo);
				Type proximoTipo2 = proximoTipo(type, 0);
				Class<?> proximaClasse2 = proximaClasse(proximoTipo2);
				return "Map.of(" + texto(proximaClasse, proximoTipo) + "," + texto(proximaClasse2, proximoTipo2) + ")";
			}
		}
		else if (List.class.isAssignableFrom(classe)) {
			if (classe == type) {
				return "List.of(1)";
			}
			else {
				Type proximoTipo = proximoTipo(type, 0);
				Class<?> proximaClasse = proximaClasse(proximoTipo);
				return "List.of(" + texto(proximaClasse, proximoTipo) + ")";
			}
		}
		else if (Set.class.isAssignableFrom(classe)) {
			if (classe == type) {
				return "Set.of(1)";
			}
			else {
				Type proximoTipo = proximoTipo(type, 0);
				Class<?> proximaClasse = proximaClasse(proximoTipo);
				return "Set.of(" + texto(proximaClasse, proximoTipo) + ")";
			}
		}
		else if (isNumber(classe) || Number.class.isAssignableFrom(classe)) {
			if (ClassUtils.isPrimitiveOrWrapper(classe)) {
				Class<?> classe2 = ClassUtils.primitiveToWrapper(classe);
				return classe2.getSimpleName() + ".valueOf(1 + \"\")";
			}
			else {
				return "new " + classe.getSimpleName() + "(1 + \"\")";
			}
		}
		else if (ClassUtils.isPrimitiveOrWrapper(classe)) {
			Class<?> classe2 = ClassUtils.primitiveToWrapper(classe);
			if (Boolean.class.isAssignableFrom(classe2)) {
				return "true";
			}
			else if (Character.class.isAssignableFrom(classe2)) {
				return "'1'";
			}
		}
		else if (String.class.isAssignableFrom(classe)) {
			return "\"1\"";
		}
		else {
			return "new " + classe.getSimpleName() + "()";
		}
		return "";
	}

	private static Type proximoTipo(Type type, int posicao) {
		ParameterizedType parameterizedType = (ParameterizedType) type;
		return parameterizedType.getActualTypeArguments()[posicao];
	}

	private static Type proximoTipo2(Type type) {
		GenericArrayType parameterizedType = (GenericArrayType) type;
		return parameterizedType.getGenericComponentType();
	}

	private static Class<?> proximaClasse(Type proximoTipo) {
		ParameterizedType parameterizedType;
		Class<?> proximaClasse = null;
		if (proximoTipo instanceof ParameterizedType) {
			parameterizedType = (ParameterizedType) proximoTipo;
			proximaClasse = (Class<?>) parameterizedType.getRawType();
		}
		else {
			proximaClasse = (Class<?>) proximoTipo;
		}
		return proximaClasse;
	}

	private static boolean isNumber(Class<?> type) {
		return type == byte.class || type == short.class || type == int.class || type == long.class || type == float.class || type == double.class;
	}


}