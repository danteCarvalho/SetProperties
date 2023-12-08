package setproperties.handlers;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
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
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaUI;
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
				ASTParser parser = ASTParser.newParser(AST.JLS19);
				parser.setResolveBindings(true);
				parser.setBindingsRecovery(true);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				Map<String, String> options = JavaCore.getOptions();
				parser.setCompilerOptions(options);
				parser.setSource(iCompilationUnit);
				CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
				NodeFinder finder = new NodeFinder(compilationUnit, iTextSelection.getOffset(), 0);
				ASTNode astNode = finder.getCoveringNode();
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
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
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
