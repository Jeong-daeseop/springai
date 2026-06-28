package com.krdevops.springai.model.masterdetail;

import com.krdevops.springai.model.crud.CrudTemplateModel;

/**
 * Master-detail FreeMarker rendering context.
 */
public record MasterDetailTemplateModel(
        CrudTemplateModel master,
        CrudTemplateModel detail,
        String fkColumn,
        String fkField
) {
    public String packageName()       { return master.packageName(); }
    public String domain()            { return master.domain(); }
    public String domainLc()          { return master.domainLc(); }
    public String domainKr()          { return master.domainKr(); }
    public String tableName()         { return master.tableName(); }
    public String urlPrefix()         { return master.urlPrefix(); }
    public String date()              { return master.date(); }
    public String egovVersion()       { return master.egovVersion(); }
    public boolean jakartaValidation(){ return master.jakartaValidation(); }
}
