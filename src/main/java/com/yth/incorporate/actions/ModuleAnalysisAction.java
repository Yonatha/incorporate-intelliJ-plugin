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
    static HashMap<Model, List<String>> artifactList = new HashMap<>();
    List<SemanticVersion> semanticVersionList = new ArrayList<>();

    public void actionPerformed(AnActionEvent e) {

        semanticVersionList.clear();
        artifactList.clear();

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
                List<String> branches = getBranches(module);
                artifactList.put(model,branches);
            }
        }
    }

    public static List<String> getBranches(VirtualFile module) {

        Repository repository = null;
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

        List<String> branches = new ArrayList<>();
        for (Ref branch : refs) {
            String brancName = branch.getName().replaceAll("refs/heads/","");
            System.out.println("Branch: " + brancName);
            branches.add(brancName);
        }
        return branches;
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
        for (Map.Entry<Model, List<String>> entry  : artifactList.entrySet()) {
            Model artifact = entry.getKey();
            List<String> branches = entry.getValue();
            List<Model> artifactListKeys = new ArrayList<>(artifactList.keySet());
            List<Dependency> dependencyList = filteredDependenciesByArtifact(artifact, artifactListKeys);

            if (dependencyList.isEmpty()) {
                String currentVersion = getVersion(artifact.getVersion(), artifact);
                addSemanticVersion(
                        artifact.getArtifactId(),
                        "",
                        currentVersion,
                        currentVersion,
                        "",
                        branches
                );
            } else {
                for (Dependency dependency : dependencyList) {

                    Model dependencyTarget = artifactList.keySet().stream()
                            .filter(x -> x.getArtifactId().equals(dependency.getArtifactId()))
                            .findFirst()
                            .orElse(null);

                    String vTarget = getVersion(dependencyTarget.getVersion(), dependencyTarget);
                    String vOrigin = getVersion(dependency.getVersion(), artifact);

                    CompatibilityStatusEnum compatibilityStatusLabel = getCompatibilityStatus(vTarget, vOrigin);
                    addSemanticVersion(
                            artifact.getArtifactId(),
                            dependency.getArtifactId(),
                            vOrigin,
                            vTarget,
                            compatibilityStatusLabel.getDescricao(),
                            branches
                    );
                }
            }
        }
    }

    private void addSemanticVersion(String artifact,
                                    String dependencyName,
                                    String dependencyCurrentVersion,
                                    String dependencyRequiredVersion,
                                    String compatibility,
                                    List<String> braches
    ) {
        SemanticVersion semanticVersion = new SemanticVersion();
        semanticVersion.setModuleName(artifact);
        semanticVersion.setDependencyName(dependencyName);
        semanticVersion.setDependencyCurrentVersion(dependencyCurrentVersion);
        semanticVersion.setDependencyRequiredVersion(dependencyRequiredVersion);
        semanticVersion.setCompatibility(compatibility);
        semanticVersion.setBranches(braches);
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
        tableModel.addColumn("Branches");
        tableModel.addColumn("Required Version");
        tableModel.addColumn("Current Version");
        tableModel.addColumn("Compatibility");

        for (SemanticVersion dependency : semanticVersionList) {
            Object[] rowData = {dependency.moduleName, dependency.dependencyName,
                    getBranchDropdown(dependency.branches), dependency.dependencyRequiredVersion, dependency.dependencyCurrentVersion, dependency.compatibility};
            tableModel.addRow(rowData);
        }

        JBTable table = new JBTable(tableModel) {
            public TableCellEditor getCellEditor(int row, int column) {
                int modelColumn = convertColumnIndexToModel(column);

                // Verificar pelo nome da coluna "Branches"
                if (getColumnName(modelColumn).equals("Branches") && row < 3) {
                    JComboBox<String> comboBox1 = getBranchDropdown(semanticVersionList.get(row).branches);
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

    private JComboBox<String> getBranchDropdown(List<String> branches) {
        JComboBox<String> comboBox = new JComboBox<>();
        for (String branch : branches) {
            comboBox.addItem(branch);
        }
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