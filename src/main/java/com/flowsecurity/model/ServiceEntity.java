package com.flowsecurity.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    private String name;

    private boolean isPublic;

    public ServiceEntity() {}

    public ServiceEntity(String name, boolean isPublic) {
        this.name = name;
        this.isPublic = isPublic;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
}
