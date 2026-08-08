package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 元素定位表
 *
 * @TableName element
 */
@TableName(value = "element")
public class Element implements Serializable {
    /**
     * 元素ID
     */
    @TableId
    private Integer id;

    /**
     * 父id
     */
    private Integer parentId;

    /**
     * 元素名称
     */
    private String elementName;

    /**
     * 类型
     */
    private Object elementType;

    /**
     * 定位类型
     */
    private Object locatorType;

    /**
     * 定位值
     */
    private String locatorValue;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 元素描述
     */
    private String description;

    /**
     * 所属项目ID
     */
    private String projectId;

    /**
     *
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /**
     *
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private String createUserId;

    /**
     * 更新人ID
     */
    private String updateUserId;

    /**
     * 是否共享元素(1-共享，0-私有)
     */
    private Integer isShared;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deletedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 元素ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * 元素ID
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * 父id
     */
    public Integer getParentId() {
        return parentId;
    }

    /**
     * 父id
     */
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    /**
     * 元素名称
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * 元素名称
     */
    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    /**
     * 类型
     */
    public Object getElementType() {
        return elementType;
    }

    /**
     * 类型
     */
    public void setElementType(Object elementType) {
        this.elementType = elementType;
    }

    /**
     * 定位类型
     */
    public Object getLocatorType() {
        return locatorType;
    }

    /**
     * 定位类型
     */
    public void setLocatorType(Object locatorType) {
        this.locatorType = locatorType;
    }

    /**
     * 定位值
     */
    public String getLocatorValue() {
        return locatorValue;
    }

    /**
     * 定位值
     */
    public void setLocatorValue(String locatorValue) {
        this.locatorValue = locatorValue;
    }

    /**
     * 排序
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * 排序
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /**
     * 元素描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 元素描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 所属项目ID
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * 所属项目ID
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     *
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     *
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     *
     */
    public Date getUpdatedAt() {
        return updatedAt;
    }

    /**
     *
     */
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 创建人ID
     */
    public String getCreateUserId() {
        return createUserId;
    }

    /**
     * 创建人ID
     */
    public void setCreateUserId(String createUserId) {
        this.createUserId = createUserId;
    }

    /**
     * 更新人ID
     */
    public String getUpdateUserId() {
        return updateUserId;
    }

    /**
     * 更新人ID
     */
    public void setUpdateUserId(String updateUserId) {
        this.updateUserId = updateUserId;
    }

    /**
     * 是否共享元素(1-共享，0-私有)
     */
    public Integer getIsShared() {
        return isShared;
    }

    /**
     * 是否共享元素(1-共享，0-私有)
     */
    public void setIsShared(Integer isShared) {
        this.isShared = isShared;
    }

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    public Integer getIsDeleted() {
        return isDeleted;
    }

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    /**
     * 删除时间
     */
    public Date getDeletedAt() {
        return deletedAt;
    }

    /**
     * 删除时间
     */
    public void setDeletedAt(Date deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        Element other = (Element) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
                && (this.getParentId() == null ? other.getParentId() == null :
                this.getParentId().equals(other.getParentId()))
                && (this.getElementName() == null ? other.getElementName() == null :
                this.getElementName().equals(other.getElementName()))
                && (this.getElementType() == null ? other.getElementType() == null :
                this.getElementType().equals(other.getElementType()))
                && (this.getLocatorType() == null ? other.getLocatorType() == null :
                this.getLocatorType().equals(other.getLocatorType()))
                && (this.getLocatorValue() == null ? other.getLocatorValue() == null :
                this.getLocatorValue().equals(other.getLocatorValue()))
                && (this.getSort() == null ? other.getSort() == null : this.getSort().equals(other.getSort()))
                && (this.getDescription() == null ? other.getDescription() == null :
                this.getDescription().equals(other.getDescription()))
                && (this.getProjectId() == null ? other.getProjectId() == null :
                this.getProjectId().equals(other.getProjectId()))
                && (this.getCreatedAt() == null ? other.getCreatedAt() == null :
                this.getCreatedAt().equals(other.getCreatedAt()))
                && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null :
                this.getUpdatedAt().equals(other.getUpdatedAt()))
                && (this.getCreateUserId() == null ? other.getCreateUserId() == null :
                this.getCreateUserId().equals(other.getCreateUserId()))
                && (this.getUpdateUserId() == null ? other.getUpdateUserId() == null :
                this.getUpdateUserId().equals(other.getUpdateUserId()))
                && (this.getIsShared() == null ? other.getIsShared() == null :
                this.getIsShared().equals(other.getIsShared()))
                && (this.getIsDeleted() == null ? other.getIsDeleted() == null :
                this.getIsDeleted().equals(other.getIsDeleted()))
                && (this.getDeletedAt() == null ? other.getDeletedAt() == null :
                this.getDeletedAt().equals(other.getDeletedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getParentId() == null) ? 0 : getParentId().hashCode());
        result = prime * result + ((getElementName() == null) ? 0 : getElementName().hashCode());
        result = prime * result + ((getElementType() == null) ? 0 : getElementType().hashCode());
        result = prime * result + ((getLocatorType() == null) ? 0 : getLocatorType().hashCode());
        result = prime * result + ((getLocatorValue() == null) ? 0 : getLocatorValue().hashCode());
        result = prime * result + ((getSort() == null) ? 0 : getSort().hashCode());
        result = prime * result + ((getDescription() == null) ? 0 : getDescription().hashCode());
        result = prime * result + ((getProjectId() == null) ? 0 : getProjectId().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        result = prime * result + ((getCreateUserId() == null) ? 0 : getCreateUserId().hashCode());
        result = prime * result + ((getUpdateUserId() == null) ? 0 : getUpdateUserId().hashCode());
        result = prime * result + ((getIsShared() == null) ? 0 : getIsShared().hashCode());
        result = prime * result + ((getIsDeleted() == null) ? 0 : getIsDeleted().hashCode());
        result = prime * result + ((getDeletedAt() == null) ? 0 : getDeletedAt().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", parentId=").append(parentId);
        sb.append(", elementName=").append(elementName);
        sb.append(", elementType=").append(elementType);
        sb.append(", locatorType=").append(locatorType);
        sb.append(", locatorValue=").append(locatorValue);
        sb.append(", sort=").append(sort);
        sb.append(", description=").append(description);
        sb.append(", projectId=").append(projectId);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", createUserId=").append(createUserId);
        sb.append(", updateUserId=").append(updateUserId);
        sb.append(", isShared=").append(isShared);
        sb.append(", isDeleted=").append(isDeleted);
        sb.append(", deletedAt=").append(deletedAt);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}