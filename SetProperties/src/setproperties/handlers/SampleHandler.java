package setproperties.handlers;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
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
				IJavaElement element = JavaUI.getEditorInputJavaElement(iEditorInput);
				ICompilationUnit iCompilationUnit = (ICompilationUnit) element;
				iCompilationUnit.becomeWorkingCopy(null);
				ITextSelection iTextSelection = (ITextSelection) iTextEditor.getSelectionProvider().getSelection();
				ASTParser parser = ASTParser.newParser(AST.JLS17);
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
				IType findType = javaProject.findType(iTypeBinding.getQualifiedName());
				IField[] fields = findType.getFields();
				StringBuilder textos = new StringBuilder();
				for (int i = 0; i < fields.length; i++) {
					IField iField = fields[i];
					String novaLinha = iTextSelection.getText() + ".set" + StringUtils.capitalize(iField.getElementName()) + "(null);\n";
					textos.append(novaLinha);
				}
				int line = document.getLineOfOffset(iTextSelection.getOffset());
				IRegion proximaLinha = document.getLineInformation(line + 1);
				document.replace(proximaLinha.getOffset(), 0, textos.toString());
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

}
