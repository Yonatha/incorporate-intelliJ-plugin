package com.yth.incorporate.ModuleAnalysis;

import java.util.List;

public class SemanticVersion {
    public String moduleName;
    public String dependencyName;
    public String dependencyCurrentVersion;
    public String dependencyRequiredVersion;
    public String compatibility;
    public List<String> branches;

    public List<String> getBranches() {
        return branches;
    }

    public void setBranches(List<String> branches) {
        this.branches = branches;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getDependencyName() {
        return dependencyName;
    }

    public void setDependencyName(String dependencyName) {
        this.dependencyName = dependencyName;
    }

    public String getDependencyCurrentVersion() {
        return dependencyCurrentVersion;
    }

    public void setDependencyCurrentVersion(String dependencyCurrentVersion) {
        this.dependencyCurrentVersion = dependencyCurrentVersion;
    }

    public String getDependencyRequiredVersion() {
        return dependencyRequiredVersion;
    }

    public void setDependencyRequiredVersion(String dependencyRequiredVersion) {
        this.dependencyRequiredVersion = dependencyRequiredVersion;
    }

    public String getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(String compatibility) {
        this.compatibility = compatibility;
    }
}
