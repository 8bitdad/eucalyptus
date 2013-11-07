/*************************************************************************
 * Copyright 2009-2013 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/

package com.eucalyptus.objectstorage.entities;

import java.util.Date;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PersistenceContext;
import javax.persistence.Table;

import org.apache.log4j.Logger;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.objectstorage.util.OSGUtil;
import com.eucalyptus.objectstorage.util.ObjectStorageProperties;
import com.eucalyptus.storage.msgs.s3.CanonicalUser;
import com.eucalyptus.storage.msgs.s3.ListEntry;
import com.eucalyptus.storage.msgs.s3.VersionEntry;

@Entity
@OptimisticLocking(type = OptimisticLockType.NONE)
@PersistenceContext(name="eucalyptus_osg")
@Table( name = "objects" )
@Cache( usage = CacheConcurrencyStrategy.TRANSACTIONAL )
public class ObjectEntity extends S3AccessControlledEntity implements Comparable {	
	@Column( name = "object_key" )
    private String objectKey;

    @Column( name = "bucket_name" )
    private String bucketName;
    
    @Column(name="version_id")
    private String versionId; //VersionId is required to uniquely identify ACLs and auth

    @Column(name="objectUuid")
    private String objectUuid; //The a uuid for this specific object content & request

	@Column(name="size")
    private Long size;

    @Column(name="storage_class")
    private String storageClass;

    @Column(name="is_deleted")
    private Boolean deleted; //Indicates this is a delete marker 
            
    @Column(name="object_last_modified") //Distinct from the record modification date, tracks the backend response
    private Date objectModifiedTimestamp;
    
    @Column(name="etag")
    private String eTag;
    
    @Column(name="deleted_date")
    private Date deletedTimestamp; //The date the object was marked for real deletion (not a delete marker)

	/**
     * Used to denote the object as a snapshot, for special access-control considerations.
     */
    @Column(name="is_snapshot")
    private Boolean isSnapshot;
 
 
    private static Logger LOG = Logger.getLogger( ObjectEntity.class );
    
    public ObjectEntity() {}

    public ObjectEntity(String bucketName, String objectKey, String versionId) {
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.versionId = versionId;
    }
    
    /**
     * Initialize this as a new object entity representing an object to PUT
     * @param bucketName
     * @param objectKey
     * @param versionId
     * @param requestId
     * @param usr
     */
    public void initializeForCreate(String bucketName, String objectKey, String versionId, String requestId, long contentLength, User usr) throws Exception {
    	this.setBucketName(bucketName);
    	this.setObjectKey(objectKey);
    	if(this.getObjectUuid() == null) {
    		//Generate a new internal key
    		this.setObjectUuid(generateInternalKey(requestId, objectKey));
    	}
    	
    	this.setDeleted(false);    	
    	
    	String ownerIamId = usr.getUserId();
    	this.setOwnerCanonicalId(usr.getAccount().getCanonicalId());
    	this.setOwnerDisplayName(usr.getAccount().getName());
    	this.setOwnerIamUserId(ownerIamId);    	
    	this.setObjectModifiedTimestamp(null);
    	this.setSize(contentLength);
    	this.setIsSnapshot(false);    	
    	this.setDeletedTimestamp(null);
    	this.setStorageClass(ObjectStorageProperties.STORAGE_CLASS.STANDARD.toString());
    }
    
    /**
     * Creates a new 'DeleteMarker' object entity from this object
     * @param versionId
     * @param requestId
     * @param usr
     * @return
     * @throws Exception
     */
    public ObjectEntity generateDeleteMarker(String versionId, String requestId, User usr) throws Exception {
    	if(versionId == null) {
    		throw new IllegalArgumentException("versionId cannot be null for delete marker generation");
    	}
    	
    	ObjectEntity deleteMarker = new ObjectEntity(this.getBucketName(), this.getObjectKey(), versionId);    	    
    	deleteMarker.setObjectUuid(generateInternalKey(requestId, objectKey));    	
    	deleteMarker.setDeleted(true);
    	
    	String ownerIamId = usr.getUserId();
    	deleteMarker.setOwnerCanonicalId(usr.getAccount().getCanonicalId());
    	deleteMarker.setOwnerIamUserId(ownerIamId);
    	deleteMarker.setOwnerDisplayName(usr.getAccount().getName());
    	deleteMarker.setObjectModifiedTimestamp(null);
    	deleteMarker.setDeletedTimestamp(null);
    	return deleteMarker;
    }
    
    public void markDeleting() {
    	this.setDeletedTimestamp(new Date());
    	this.setVersionId(null);
    }
        
    public void finalizeCreation(@Nullable String versionId, @Nullable Date lastModified, @Nonnull String etag) throws Exception {
    	this.seteTag(etag);
    	if(lastModified != null) {
    		this.setObjectModifiedTimestamp(lastModified);
    	} else {
    		this.setObjectModifiedTimestamp(new Date());
    	}
    	
    	this.setVersionId(versionId);
    }
    
	private static String generateInternalKey(String requestId, String key) {
		return key + "-" + requestId;
	}
	
    public Date getDeletedTimestamp() {
		return deletedTimestamp;
	}

	public void setDeletedTimestamp(Date deletedTimestamp) {
		this.deletedTimestamp = deletedTimestamp;
	}

    
	public String geteTag() {
		return eTag;
	}

	public void seteTag(String eTag) {
		this.eTag = eTag;
	}

	public Date getObjectModifiedTimestamp() {
		return objectModifiedTimestamp;
	}

	public void setObjectModifiedTimestamp(Date objectModifiedTimestamp) {
		this.objectModifiedTimestamp = objectModifiedTimestamp;
	}
    
	@Override
	public String getResourceFullName() {
		return getBucketName() + "/" + getObjectKey();
	}

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }
    
    public Boolean getDeleted() {
		return deleted;
	}

	public void setDeleted(Boolean deleted) {
		this.deleted = deleted;
	}

	public String getVersionId() {
		return versionId;
	}

	public void setVersionId(String versionId) {
		this.versionId = versionId;
	}

	public int compareTo(Object o) {
        return this.objectKey.compareTo(((ObjectEntity)o).getObjectKey());
    }

	public boolean isPending() {
		return (getObjectModifiedTimestamp() == null);
	}
	
	public String getObjectUuid() {
		return objectUuid;
	}

	public void setObjectUuid(String objectUuid) {
		this.objectUuid = objectUuid;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((bucketName == null) ? 0 : bucketName.hashCode());
		result = prime * result
				+ ((objectKey == null) ? 0 : objectKey.hashCode());
		result = prime * result
				+ ((versionId == null) ? 0 : versionId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ObjectEntity other = (ObjectEntity) obj;
		if (bucketName == null) {
			if (other.bucketName != null)
				return false;
		} else if (!bucketName.equals(other.bucketName))
			return false;
		
		if (objectKey == null) {
			if (other.objectKey != null)
				return false;
		} else if (!objectKey.equals(other.objectKey))
			return false;
		
		if (versionId == null) {
			if (other.versionId != null)
				return false;
		} else if (!versionId.equals(other.versionId))
			return false;
		
		if (objectUuid == null) {
			if (other.objectUuid != null)
				return false;
		} else if (!objectUuid.equals(other.objectUuid))
			return false;
		
		return true;
	}

	public Boolean getIsSnapshot() {
		return isSnapshot;
	}

	public void setIsSnapshot(Boolean isSnapshot) {
		this.isSnapshot = isSnapshot;
	}
	
	public static Criterion getNotSnapshotRestriction() {
		return Restrictions.ne("isSnapshot", true);
	}
	
	public static Criterion getNotPendingRestriction() {
		return Restrictions.isNotNull("objectModifiedTimestamp");
	}
	
	/* versionId == null && is_deleted == true
	 * if versionId != null, then is_deleted indicates a deleteMarker
	 */
	public static Criterion getNotDeletingRestriction() {
		return Restrictions.isNull("deletedTimestamp");
	}
	
	/**
	 * Return a ListEntry for this entity
	 * @return
	 */
	public ListEntry toListEntry() {
		ListEntry e = new ListEntry();
		e.setEtag(this.geteTag());
		e.setKey(this.getObjectKey());
		e.setLastModified(OSGUtil.dateToHeaderFormattedString(this.getObjectModifiedTimestamp()));
		e.setSize(this.getSize());
		e.setStorageClass(this.getStorageClass());
		e.setOwner(new CanonicalUser(this.getOwnerCanonicalId(), this.getOwnerDisplayName()));		
		return e;
	}
	
	/**
	 * Return a VersionEntry for this entity
	 * @return
	 */
	public VersionEntry toVersionEntry() {
		VersionEntry e = new VersionEntry();
		e.setEtag(this.geteTag());
		e.setKey(this.getObjectKey());
		e.setVersionId(this.getVersionId());
		e.setLastModified(OSGUtil.dateToHeaderFormattedString(this.getObjectModifiedTimestamp()));
		e.setSize(this.getSize());
		
		//TODO: FIXME!!!
		e.setIsLatest(false);
		
		String displayName = "";
		try {
			displayName = Accounts.lookupAccountByCanonicalId(this.getOwnerCanonicalId()).getName();
		} catch(Exception ex) {
			LOG.error("Failed to get display name/account name for canonical Id: " + this.getOwnerCanonicalId(),ex);
			displayName = "";
		}
		e.setOwner(new CanonicalUser(this.getOwnerCanonicalId(), displayName));
		return e;
	}
	
	//TODO: add delete marker support. Fix is to use super-type for versioning entry and sub-types for version vs deleteMarker
}
