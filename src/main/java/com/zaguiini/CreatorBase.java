package com.zaguiini;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration;
import com.intellij.lang.ecmascript6.psi.ES6ExportSpecifier;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Properties;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

public abstract class CreatorBase extends AnAction {
    protected String getTemplate() {
        return "";
    }

    private void addToFile(Project project, VirtualFile virtualFile, String componentName) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(psiFile));
        PsiElement[] children = psiFile.getChildren();

        int position = -1;

        for(int i = 0; i < children.length; i++) {
            PsiElement el = children[i];

            if (el instanceof ES6ExportDeclaration) {
                ES6ExportDeclaration export = (ES6ExportDeclaration) el;
                ES6ExportSpecifier[] specifiers = export.getExportSpecifiers();

                if(specifiers.length > 0) {
                    String existingExportName = specifiers[specifiers.length - 1].getDeclaredName();

                    if(componentName.toLowerCase().compareTo(existingExportName.toLowerCase()) <= 0) {
                        position = i;
                        break;
                    }
                }
            }
        }

        int finalPosition = position;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElement newExport = ExportDeclaration.create(project, componentName);

            if(finalPosition == -1) {
                psiFile.addAfter(newExport, children.length > 0 ? children[children.length - 1] : null);
            } else {
                psiFile.addBefore(newExport, children[finalPosition]);
            }
        });

        FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(psiFile));
    }

    private PsiElement writeFile(String template, String fileName, Project project, Properties attributes, PsiDirectory dir) throws Exception {
        FileTemplateManager templateManager = FileTemplateManager.getInstance(project);
        FileTemplate componentTemplate = templateManager.getInternalTemplate(template);
        return FileTemplateUtil.createFromTemplate(componentTemplate, fileName, attributes, dir);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile entry = event.getData(CommonDataKeys.VIRTUAL_FILE);

        if(project == null || entry == null) {
            return;
        }

        VirtualFile parentFolder = Helpers.getPath(entry);
        String componentName = Helpers.getComponentName();

        if(!Helpers.isValidComponentName(componentName)) {
            return;
        }

        Properties attributes = new Properties();
        attributes.put("ComponentName", componentName);

        PsiManager manager = PsiManager.getInstance(project);

        if(Helpers.hasExistingFolder(parentFolder, componentName)) {
            Messages.showInfoMessage("Directory already exists", Helpers.ERROR_TITLE);
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            manager.findDirectory(parentFolder).createSubdirectory(componentName);
        });

        try {
            writeFile("ComponentIndex.ts", "index.ts", project, attributes, manager.findDirectory(entry).findSubdirectory(componentName));
            PsiElement el = writeFile(getTemplate(), componentName, project, attributes, manager.findDirectory(entry).findSubdirectory(componentName));
            CreateFromTemplateActionBase.startLiveTemplate(el.getContainingFile());
            FileEditorManager.getInstance(project).openFile(el.getContainingFile().getVirtualFile(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        VirtualFile parentIndexTSFile = parentFolder.findFileByRelativePath("index.ts");
        VirtualFile parentIndexTSXFile = parentFolder.findFileByRelativePath("index.tsx");

        if(parentIndexTSFile != null) {
            addToFile(project, parentIndexTSFile, componentName);
        } else if(parentIndexTSXFile != null) {
            addToFile(project, parentIndexTSXFile, componentName);
        } else {
            try {
                writeFile("ParentExport.ts", "index.ts", project, attributes, manager.findDirectory(parentFolder));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
