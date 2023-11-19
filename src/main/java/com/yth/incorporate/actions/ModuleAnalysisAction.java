package com.yth.incorporate.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.JBTable;
import com.yth.incorporate.ModuleAnalysis.CompatibilityStatusEnum;
import com.yth.incorporate.ModuleAnalysis.ModuleBranches;
import com.yth.incorporate.ModuleAnalysis.SemanticVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ModuleAnalysisAction extends AnAction {
    static List<Model> artifactList = new ArrayList<>();
    List<SemanticVersion> semanticVersionList = new ArrayList<>();
    List<ModuleBranches> moduleBranches = new ArrayList<>();

    public void actionPerformed(AnActionEvent e) {

        semanticVersionList.clear();
        artifactList.clear();
        moduleBranches.clear();

        try {
            loadArtifactList();
        } catch (GitAPIException ex) {
            throw new RuntimeException(ex);
        }
        loadSemanticVersionList();
        displayGUI();
    }

    public void loadArtifactList() throws GitAPIException {
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        VirtualFile[] moduleContentRoots = ProjectRootManager.getInstance(project).getContentRootsFromAllModules();
        List<VirtualFile> projectModulesList = getModuleNames(moduleContentRoots);

        for (VirtualFile module : projectModulesList) {
            Model model = getArtifactId(module);
            if (model.getArtifactId() != null) {

                ModuleBranches mb = new ModuleBranches();
                mb.setModuleName(model.getArtifactId());
                mb.setCurrentBranch(getCurrentBranch(module));
                mb.setBranches(getBranches(module));
                mb.setModel(model);
                moduleBranches.add(mb);

                artifactList.add(model);
            }
        }
    }

    public static List<String> getBranches(VirtualFile module) {

        List<String> branches = new ArrayList<>();
        try (Git git = Git.open(new File(module.getPath()))) {
            List<Ref> b = git.branchList().call();
            System.out.println("Branches locais:");
            for (Ref branch : b) {
                String brancName = branch.getName().replaceAll("refs/heads/", "");
                branches.add(brancName);
            }
            return branches;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }

        return branches;
    }

    public static List<String> getBranchesOld(VirtualFile module) {

        List<String> branches = new ArrayList<>();

        File gitDir = new File(module.getPath(), ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            Repository repository;
            try {
                repository = Git.open(new File(module.getPath())).getRepository();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String repositoryUrl = repository.getConfig().getString("remote", "origin", "url");

            if (repositoryUrl.contains("@"))
                repositoryUrl = convertSshToHttps(repositoryUrl);

            Collection<Ref> refs = null;
            try {
                refs = Git.lsRemoteRepository()
                        .setHeads(true)
                        .setTags(true)
                        .setRemote(repositoryUrl)
                        .call();
            } catch (GitAPIException e) {
                throw new RuntimeException(e);
            }


            for (Ref branch : refs) {
                String brancName = branch.getName().replaceAll("refs/heads/", "");
                System.out.println("Branch: " + brancName);
                branches.add(brancName);
            }
        }

        return branches;
    }

    public String getCurrentBranch(VirtualFile module) {
        String currentBranch = null;
        try (Git git = Git.open(new File(module.getPath()))) {
            Repository repository = git.getRepository();
            Ref head = repository.exactRef("HEAD");
            currentBranch = repository.getBranch();

            System.out.println("Branch atual: " + currentBranch);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return currentBranch;
    }

    public static String convertSshToHttps(String sshUrl) {

        URIish uri = null;
        try {
            uri = new URIish(sshUrl);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String host = uri.getHost();
        String path = uri.getPath();
        String user = uri.getUser();
        if (user != null && !user.isEmpty())
            host = host.replace(user + "@", "");

        return "https://" + host + "/" + path;
    }

    public void loadSemanticVersionList() {
        for (Model artifact : artifactList) {
            List<Dependency> dependencyList = filteredDependenciesByArtifact(artifact, artifactList);

            if (dependencyList.isEmpty()) {
                String currentVersion = getVersion(artifact.getVersion(), artifact);
                addSemanticVersion(
                        artifact.getArtifactId(),
                        "",
                        currentVersion,
                        currentVersion,
                        ""
                );
            } else {
                for (Dependency dependency : dependencyList) {

                    Model dependencyTarget = artifactList.stream().filter(x -> x.getArtifactId().equals(dependency.getArtifactId())).findFirst().orElse(null);
                    String vTarget = getVersion(dependencyTarget.getVersion(), dependencyTarget);
                    String vOrigin = getVersion(dependency.getVersion(), artifact);

                    CompatibilityStatusEnum compatibilityStatusLabel = getCompatibilityStatus(vTarget, vOrigin);
                    addSemanticVersion(
                            artifact.getArtifactId(),
                            dependency.getArtifactId(),
                            vOrigin,
                            vTarget,
                            compatibilityStatusLabel.getDescricao()
                    );
                }
            }
        }
    }

    private void addSemanticVersion(String artifact,
                                    String dependencyName,
                                    String dependencyCurrentVersion,
                                    String dependencyRequiredVersion,
                                    String compatibility
    ) {
        SemanticVersion semanticVersion = new SemanticVersion();
        semanticVersion.setModuleName(artifact);
        semanticVersion.setDependencyName(dependencyName);
        semanticVersion.setDependencyCurrentVersion(dependencyCurrentVersion);
        semanticVersion.setDependencyRequiredVersion(dependencyRequiredVersion);
        semanticVersion.setCompatibility(compatibility);

        if (!moduleBranches.isEmpty()) {
            ModuleBranches mb = moduleBranches.stream().filter(x -> x.getModuleName().equals(artifact)).findFirst().orElse(null);
            semanticVersion.setBranches(mb.getBranches());
            semanticVersion.setModuleCurrentBranch(mb.getCurrentBranch());
        }

        semanticVersionList.add(semanticVersion);
    }

    public CompatibilityStatusEnum getCompatibilityStatus(String x, String y) {
        boolean dependencyResult = y.equals(x);
        if (dependencyResult)
            return CompatibilityStatusEnum.COMPATIVEL;

        return CompatibilityStatusEnum.INCOMPATIVEL;
    }


    public List<VirtualFile> getModuleNames(VirtualFile[] moduleContentRoots) {
        List<VirtualFile> projectModulesList = new ArrayList<>();
        for (VirtualFile moduleContentRoot : moduleContentRoots) {
            projectModulesList.add(moduleContentRoot);
        }
        return projectModulesList;
    }

    public Document getDocument(VirtualFile moduleName) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(moduleName.getPath() + "/pom.xml");
    }

    public NodeList getChildNodes(VirtualFile moduleName) throws ParserConfigurationException, IOException, SAXException {
        Document document = getDocument(moduleName);
        NodeList nodeList = document.getElementsByTagName("properties");
        Node propertiesNode = nodeList.item(0);
        return propertiesNode.getChildNodes();
    }

    public Model getArtifactId(VirtualFile module) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = new Model();
        String caminho = module.getPath() + "/pom.xml";
        if (new File(caminho).exists()) {
            try {
                model = reader.read(new FileReader(caminho));
                return model;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (XmlPullParserException e) {
                throw new RuntimeException(e);
            }
        }
        return model;
    }

    public List<Dependency> filteredDependenciesByArtifact(Model model, List<Model> artifactList) {
        List<Dependency> filteredDependencies = model.getDependencies().stream()
                .filter(dependency ->
                        artifactList.stream()
                                .anyMatch(artifact -> artifact.getArtifactId()
                                        .contains(dependency.getArtifactId())
                                )
                )
                .collect(Collectors.toList());
        return filteredDependencies;
    }

    public void displayGUI() {
        DefaultTableModel tableModel = new DefaultTableModel();
        tableModel.addColumn("Module");
        tableModel.addColumn("Dependency");
        tableModel.addColumn("Branch");
        tableModel.addColumn("Required Version");
        tableModel.addColumn("Current Version");
        tableModel.addColumn("Compatibility");

        for (SemanticVersion dependency : semanticVersionList) {
            Object[] rowData = {
                    dependency.moduleName,
                    dependency.dependencyName,
                    dependency.getModuleCurrentBranch(),
                    dependency.dependencyRequiredVersion,
                    dependency.dependencyCurrentVersion,
                    dependency.compatibility
            };
            tableModel.addRow(rowData);
        }

        JBTable table = new JBTable(tableModel) {
            public TableCellEditor getCellEditor(int row, int column) {
                int modelColumn = convertColumnIndexToModel(column);
                if (getColumnName(modelColumn).equals("Branch") && row < 2) {
                    JComboBox<String> comboBox1 = getBranchDropdown(semanticVersionList.get(row));

                    comboBox1.validate();
                    comboBox1.repaint();

                    String currentBranch = semanticVersionList.get(row).getModuleCurrentBranch();
                    if (currentBranch != null && semanticVersionList.get(row).branches.contains(currentBranch)) {
                        comboBox1.setSelectedItem(currentBranch);
                    }

                    return new DefaultCellEditor(comboBox1);
                } else {
                    return super.getCellEditor(row, column);
                }
            }
        };

        JScrollPane scrollPane = new JScrollPane(table);
        DialogWrapper dialog = new DialogWrapper(true) {
            {
                init();
                setTitle("Module Analysis");
                setSize(600, 300);
            }

            @Override
            protected JComponent createCenterPanel() {
                return scrollPane;
            }
        };

        dialog.show();
    }

    private JComboBox<String> getBranchDropdown(SemanticVersion semanticVersion) {
        JComboBox<String> comboBox = new JComboBox<>();
        List<String> branches = semanticVersion.getBranches();
        String currentBranch = semanticVersion.getModuleCurrentBranch();

        for (String branch : branches) {
            comboBox.addItem(branch);
        }

        if (currentBranch != null && branches.contains(currentBranch))
            comboBox.setSelectedItem(currentBranch);

        return comboBox;
    }

    public static String getVersion(String v, Model model) {
        Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
        Matcher matcher = pattern.matcher(v);
        if (matcher.find()) {
            String variableName = matcher.group(1);
            Properties properties = model.getProperties();
            return properties.getProperty(variableName);
        }
        return v;
    }
}