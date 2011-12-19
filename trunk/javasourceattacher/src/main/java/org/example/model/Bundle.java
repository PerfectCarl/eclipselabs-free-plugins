package org.example.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Bundle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String binMd5;
    private String sourceMd5;
    private String sourceUrl;

    // Accessors for the fields.  JPA doesn't use these, but your application does.

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
	public String getBinMd5() {
		return binMd5;
	}
	public void setBinMd5(String binMd5) {
		this.binMd5 = binMd5;
	}
	public String getSourceMd5() {
		return sourceMd5;
	}
	public void setSourceMd5(String sourceMd5) {
		this.sourceMd5 = sourceMd5;
	}
	public String getSourceUrl() {
		return sourceUrl;
	}
	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}


}