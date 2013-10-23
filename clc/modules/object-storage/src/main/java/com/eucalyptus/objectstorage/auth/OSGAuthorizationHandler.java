package com.eucalyptus.objectstorage.auth;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;

import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.Permissions;
import com.eucalyptus.auth.policy.PolicySpec;
import com.eucalyptus.auth.principal.Account;
import com.eucalyptus.auth.principal.Principals;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.context.Context;
import com.eucalyptus.context.Contexts;
import com.eucalyptus.context.NoSuchContextException;
import com.eucalyptus.objectstorage.entities.S3AccessControlledEntity;
import com.eucalyptus.objectstorage.msgs.ObjectStorageRequestType;
import com.eucalyptus.objectstorage.policy.AdminOverrideAllowed;
import com.eucalyptus.objectstorage.policy.RequiresACLPermission;
import com.eucalyptus.objectstorage.policy.RequiresPermission;
import com.eucalyptus.objectstorage.policy.ResourceType;
import com.eucalyptus.objectstorage.providers.s3.S3ProviderClient;
import com.eucalyptus.objectstorage.util.ObjectStorageProperties;
import com.eucalyptus.system.Ats;
import com.google.common.base.Strings;

public class OSGAuthorizationHandler implements RequestAuthorizationHandler {
	private static final Logger LOG = Logger.getLogger(OSGAuthorizationHandler.class); 
	private static final RequestAuthorizationHandler authzHandler = new OSGAuthorizationHandler();
	
	public static RequestAuthorizationHandler getInstance() {
		return authzHandler;
	}
	
	/**
	 * Does the current request have an authenticated user? Or is it anonymous?
	 * @return
	 */
	protected static boolean isUserAnonymous(User usr) {
		return Principals.nobodyUser().equals(usr);
	}

	/**
	 * Evaluates the authorization for the operation requested, evaluates IAM, ACL, and bucket policy (bucket policy not yet supported).
	 * @param request
	 * @param optionalResourceId optional (can be null) explicit resourceId to check. If null, the request is used to get the resource.
	 * @param optionalOwnerId optional (can be null) owner Id for the resource being evaluated.
	 * @param optionalResourceAcl option acl for the requested resource
	 * @param resourceAllocationSize the size for the quota check(s) if applicable
	 * @return
	 */
	public <T extends ObjectStorageRequestType> boolean operationAllowed(@Nonnull T request, @Nullable final S3AccessControlledEntity bucketResourceEntity, @Nullable final S3AccessControlledEntity objectResourceEntity, long resourceAllocationSize) throws IllegalArgumentException {
		/*
		 * Process the operation's authz requirements based on the request type annotations
		 */
		Ats requestAuthzProperties = Ats.from(request);
		ObjectStorageProperties.Permission[] requiredBucketACLPermissions = null;
		ObjectStorageProperties.Permission[] requiredObjectACLPermissions = null;
		Boolean allowOwnerOnly = null;
		RequiresACLPermission requiredACLs = requestAuthzProperties.get(RequiresACLPermission.class);
		if(requiredACLs != null) {
			requiredBucketACLPermissions = requiredACLs.bucket();
			requiredObjectACLPermissions = requiredACLs.object();
			allowOwnerOnly = requiredACLs.ownerOnly();
		} else {
			//No ACL annotation is ok, maybe a admin only op
		}
		
		String[] requiredActions = null;
		RequiresPermission perms = requestAuthzProperties.get(RequiresPermission.class);
		if(perms != null) {
			requiredActions = perms.value(); 
		}

		Boolean allowAdmin = (requestAuthzProperties.get(AdminOverrideAllowed.class) != null);
		Boolean allowOnlyAdmin = (requestAuthzProperties.get(AdminOverrideAllowed.class) != null) && requestAuthzProperties.get(AdminOverrideAllowed.class).adminOnly();
		
		//Must have at least one of: admin-only, owner-only, ACL, or IAM.
		if(requiredBucketACLPermissions == null && 
				requiredObjectACLPermissions == null &&
				requiredActions == null &&
				!allowAdmin) {
			//Insufficient permission set on the message type.
			throw new IllegalArgumentException("Insufficient permission annotations on type: " + request.getClass().getName() + " cannot evaluate authorization");
		}
		
		String resourceType = null;
		if(requestAuthzProperties.get(ResourceType.class) != null) {
			resourceType = requestAuthzProperties.get(ResourceType.class).value();
		}
		
		//Use these variables to isolate where all the AuthExceptions can happen on account/user lookups
		User requestUser = null;
		Account requestAccount = null;
		Context ctx = null;
		try {
			try {
				ctx = Contexts.lookup(request.getCorrelationId());
			} catch(NoSuchContextException e) {
				ctx = null;
			}
			
			if(ctx != null) { 
				requestUser = ctx.getUser();
			}
			
			//This is not an expected path, but if no context found use the request credentials itself
			if(requestUser == null && !Strings.isNullOrEmpty(request.getEffectiveUserId())) {
				requestUser = Accounts.lookupUserById(request.getEffectiveUserId());
				requestAccount = requestUser.getAccount();
			}
			
			if(requestUser == null && !Strings.isNullOrEmpty(request.getAccessKeyID())) {
				requestUser = Accounts.lookupUserByAccessKeyId(request.getAccessKeyID());
				requestAccount = requestUser.getAccount();
			}
		} catch (AuthException e) {
			LOG.error("Failed to get user for request, cannot verify authorization: " + e.getMessage(), e);				
			return false;
		}
		
		
		if(allowAdmin && requestUser.isSystemAdmin()) {
			//Admin override
			return true;
		}
		
		Account resourceOwnerAccount = null;
		if(resourceType == null) {
			throw new IllegalArgumentException("No resource type found in request class annotations, cannot process.");
		} else {
			try {
				//Ensure we have the proper resource entities present and get owner info						
				if(PolicySpec.S3_RESOURCE_BUCKET.equals(resourceType)) {
					//Get the bucket owner.
					if(bucketResourceEntity == null) {
						LOG.error("Could not check access for operation due to no bucket resource entity found");
						return false;
					}
					resourceOwnerAccount = Accounts.lookupAccountByCanonicalId(bucketResourceEntity.getOwnerCanonicalId());
				} else if(PolicySpec.S3_RESOURCE_OBJECT.equals(resourceType)) {
					if(objectResourceEntity == null) {
						LOG.error("Could not check access for operation due to no object resource entity found");
						return false;
					}
					resourceOwnerAccount = Accounts.lookupAccountByCanonicalId(objectResourceEntity.getOwnerCanonicalId());
				}
			} catch(AuthException e) {
				LOG.error("Exception caught looking up resource owner. Disallowing operation.",e);
				return false;
			}
		}
		
		//Get the resourceId based on IAM resource type
		String resourceId = null;
		if(resourceId == null ) {
			if(resourceType.equals(PolicySpec.S3_RESOURCE_BUCKET)) {
				resourceId = request.getBucket();
			} else if(resourceType.equals(PolicySpec.S3_RESOURCE_OBJECT)) {
				resourceId = request.getKey();
			}
		}
		
		if(requiredBucketACLPermissions == null && requiredObjectACLPermissions == null) {
			throw new IllegalArgumentException("No requires-permission actions found in request class annotations, cannot process.");
		}

		/* ACL Checks */
		//Is the user's account allowed?
		Boolean aclAllow = false;
		
		if(requiredBucketACLPermissions != null && requiredBucketACLPermissions.length > 0) {
			//Check bucket ACLs
			
			if(bucketResourceEntity == null) {
				//There are bucket ACL requirements but no bucket entity to check. fail.
				//Don't bother with other checks, this is an invalid state
				throw new IllegalArgumentException("Null bucket resource, cannot evaluate bucket ACL");
			}
			
			//Evaluate the bucket ACL, any matching grant gives permission
			for(ObjectStorageProperties.Permission permission : requiredBucketACLPermissions) {
				aclAllow = aclAllow || bucketResourceEntity.can(permission, requestAccount.getCanonicalId());
			}
		}
		
		//Check object ACLs, if any
		if(requiredObjectACLPermissions != null && requiredObjectACLPermissions.length > 0) {
			if(objectResourceEntity == null) {
				//There are object ACL requirements but no object entity to check. fail.
				//Don't bother with other checks, this is an invalid state				
				throw new IllegalArgumentException("Null bucket resource, cannot evaluate bucket ACL");
			}
			for(ObjectStorageProperties.Permission permission : requiredObjectACLPermissions) {
				aclAllow = aclAllow || objectResourceEntity.can(permission, requestAccount.getCanonicalId());
			}
		}

		/* Resource owner only? if so, override any previous acl decisions
		 * It is not expected that owneronly is set as well as other ACL permissions,
		 * Regular owner permissions (READ, WRITE, READ_ACP, WRITE_ACP) are handled by the regular acl checks.
		 * OwnerOnly should be only used for operations not covered by the other Permissions (e.g. logging, or versioning)
		 */
		aclAllow = (allowOwnerOnly ? resourceOwnerAccount.getAccountNumber().equals(requestAccount.getAccountNumber()) : aclAllow);
		
		/* IAM checks for user */		
		Boolean iamAllow = true;
		//Evaluate each iam action required, all must be allowed
		for(String action : requiredActions ) {
			/*Permissions.isAuthorized(vendor, resourceType, resourceName, resourceAccount, action, requestUser);
			Permissions.canAllocate(
					PolicySpec.VENDOR_S3,
					PolicySpec.S3_RESOURCE_BUCKET, "",
					PolicySpec.S3_CREATEBUCKET, ctx.getUser(), 1L)
					*/
			iamAllow = Permissions.isAuthorized(PolicySpec.VENDOR_S3,
					resourceType, resourceId,
					resourceOwnerAccount , action,
					requestUser) && Permissions.canAllocate(
					PolicySpec.VENDOR_S3,
					resourceType, resourceId,
					action, requestUser, resourceAllocationSize);
			//iamAllow = iamAllow && !Lookups.checkPrivilege(action, PolicySpec.VENDOR_S3, resourceType, resourceId, resourceOwnerAccountId);
		}
		
		return aclAllow && iamAllow;
	}

}
