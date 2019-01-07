package reciter.scopus.model;

import java.util.List;

public class Pmids {
    private List<Object> pmids;
    private String type;

    public Pmids() {}

    public Pmids(List<Object> pmids) {
        this.pmids = pmids;
    }
    public List<Object> getPmids() {
        return pmids;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
}
