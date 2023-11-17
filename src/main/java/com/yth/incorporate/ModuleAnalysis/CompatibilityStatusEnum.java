package com.yth.incorporate.ModuleAnalysis;

public enum CompatibilityStatusEnum {

    COMPATIVEL("OK"),
    INCOMPATIVEL("Incompatible"),
    SELF("");

    private String descricao;

    CompatibilityStatusEnum(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
