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

package com.eucalyptus.objectstorage;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.principal.Account;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.component.ComponentIds;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.component.annotation.ServiceOperation;
import com.eucalyptus.component.id.Eucalyptus;
import com.eucalyptus.configurable.ConfigurableClass;
import com.eucalyptus.configurable.ConfigurableProperty;
import com.eucalyptus.configurable.PropertyDirectory;
import com.eucalyptus.context.Context;
import com.eucalyptus.context.Contexts;
import com.eucalyptus.context.NoSuchContextException;
import com.eucalyptus.objectstorage.auth.OSGAuthorizationHandler;
import com.eucalyptus.objectstorage.bittorrent.Tracker;
import com.eucalyptus.objectstorage.entities.Bucket;
import com.eucalyptus.objectstorage.entities.ObjectEntity;
import com.eucalyptus.objectstorage.entities.ObjectStorageGatewayInfo;
import com.eucalyptus.objectstorage.entities.S3AccessControlledEntity;
import com.eucalyptus.objectstorage.exceptions.s3.AccountProblemException;
import com.eucalyptus.objectstorage.exceptions.s3.BucketNotEmptyException;
import com.eucalyptus.objectstorage.exceptions.s3.InvalidArgumentException;
import com.eucalyptus.objectstorage.exceptions.s3.InvalidBucketNameException;
import com.eucalyptus.objectstorage.exceptions.s3.MalformedACLErrorException;
import com.eucalyptus.objectstorage.exceptions.s3.MissingContentLengthException;
import com.eucalyptus.objectstorage.exceptions.s3.NoSuchKeyException;
import com.eucalyptus.objectstorage.exceptions.s3.NoSuchVersionException;
import com.eucalyptus.objectstorage.exceptions.s3.NotImplementedException;
import com.eucalyptus.objectstorage.exceptions.s3.S3Exception;
import com.eucalyptus.objectstorage.exceptions.s3.TooManyBucketsException;
import com.eucalyptus.objectstorage.exceptions.s3.AccessDeniedException;
import com.eucalyptus.objectstorage.exceptions.s3.InternalErrorException;
import com.eucalyptus.objectstorage.exceptions.s3.NoSuchBucketException;
import com.eucalyptus.objectstorage.msgs.CopyObjectResponseType;
import com.eucalyptus.objectstorage.msgs.CopyObjectType;
import com.eucalyptus.objectstorage.msgs.CreateBucketResponseType;
import com.eucalyptus.objectstorage.msgs.CreateBucketType;
import com.eucalyptus.objectstorage.msgs.DeleteBucketResponseType;
import com.eucalyptus.objectstorage.msgs.DeleteBucketType;
import com.eucalyptus.objectstorage.msgs.DeleteObjectResponseType;
import com.eucalyptus.objectstorage.msgs.DeleteObjectType;
import com.eucalyptus.objectstorage.msgs.DeleteVersionResponseType;
import com.eucalyptus.objectstorage.msgs.DeleteVersionType;
import com.eucalyptus.objectstorage.msgs.GetBucketAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.GetBucketAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.GetBucketLocationResponseType;
import com.eucalyptus.objectstorage.msgs.GetBucketLocationType;
import com.eucalyptus.objectstorage.msgs.GetBucketLoggingStatusResponseType;
import com.eucalyptus.objectstorage.msgs.GetBucketLoggingStatusType;
import com.eucalyptus.objectstorage.msgs.GetBucketVersioningStatusResponseType;
import com.eucalyptus.objectstorage.msgs.GetBucketVersioningStatusType;
import com.eucalyptus.objectstorage.msgs.GetObjectAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.GetObjectAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.GetObjectExtendedResponseType;
import com.eucalyptus.objectstorage.msgs.GetObjectExtendedType;
import com.eucalyptus.objectstorage.msgs.GetObjectResponseType;
import com.eucalyptus.objectstorage.msgs.GetObjectStorageConfigurationResponseType;
import com.eucalyptus.objectstorage.msgs.GetObjectStorageConfigurationType;
import com.eucalyptus.objectstorage.msgs.GetObjectType;
import com.eucalyptus.objectstorage.msgs.HeadBucketResponseType;
import com.eucalyptus.objectstorage.msgs.HeadBucketType;
import com.eucalyptus.objectstorage.msgs.ListAllMyBucketsResponseType;
import com.eucalyptus.objectstorage.msgs.ListAllMyBucketsType;
import com.eucalyptus.objectstorage.msgs.ListBucketResponseType;
import com.eucalyptus.objectstorage.msgs.ListBucketType;
import com.eucalyptus.objectstorage.msgs.ListVersionsResponseType;
import com.eucalyptus.objectstorage.msgs.ListVersionsType;
import com.eucalyptus.objectstorage.msgs.ObjectStorageRequestType;
import com.eucalyptus.objectstorage.msgs.PostObjectResponseType;
import com.eucalyptus.objectstorage.msgs.PostObjectType;
import com.eucalyptus.objectstorage.msgs.PutObjectResponseType;
import com.eucalyptus.objectstorage.msgs.PutObjectType;
import com.eucalyptus.objectstorage.msgs.SetBucketLoggingStatusResponseType;
import com.eucalyptus.objectstorage.msgs.SetBucketLoggingStatusType;
import com.eucalyptus.objectstorage.msgs.SetBucketVersioningStatusResponseType;
import com.eucalyptus.objectstorage.msgs.SetBucketVersioningStatusType;
import com.eucalyptus.objectstorage.msgs.SetRESTBucketAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.SetRESTBucketAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.SetRESTObjectAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.SetRESTObjectAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.UpdateObjectStorageConfigurationResponseType;
import com.eucalyptus.objectstorage.msgs.UpdateObjectStorageConfigurationType;
import com.eucalyptus.objectstorage.util.AclUtils;
import com.eucalyptus.objectstorage.util.ObjectStorageProperties;
import com.eucalyptus.storage.msgs.s3.AccessControlList;
import com.eucalyptus.storage.msgs.s3.AccessControlPolicy;
import com.eucalyptus.storage.msgs.s3.BucketListEntry;
import com.eucalyptus.storage.msgs.s3.CanonicalUser;
import com.eucalyptus.storage.msgs.s3.Grant;
import com.eucalyptus.storage.msgs.s3.Grantee;
import com.eucalyptus.storage.msgs.s3.ListAllMyBucketsList;
import com.eucalyptus.storage.msgs.s3.ListEntry;
import com.eucalyptus.storage.msgs.s3.LoggingEnabled;
import com.eucalyptus.storage.msgs.s3.PrefixEntry;
import com.eucalyptus.storage.msgs.s3.TargetGrants;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.gwt.thirdparty.guava.common.collect.Iterables;

import edu.ucsb.eucalyptus.msgs.BaseDataChunk;
import edu.ucsb.eucalyptus.msgs.ComponentProperty;
import edu.ucsb.eucalyptus.util.SystemUtil;

/**
 * Operation handler for the ObjectStorageGateway. Main point of entry
 * This class handles user and system requests.
 *
 */
public class ObjectStorageGateway implements ObjectStorageService {
	private static Logger LOG = Logger.getLogger( ObjectStorageGateway.class );

	private static ObjectStorageProviderClient ospClient = null;
	protected static ConcurrentHashMap<String, ChannelBuffer> streamDataMap = new ConcurrentHashMap<String, ChannelBuffer>();
	protected static final String USR_EMAIL_KEY = "email";//lookup for account admins email

	private static final Random rand = new Random(System.currentTimeMillis()); //for anything that needs randomization
	
	private static final int REAPER_POOL_SIZE = 2;	
	private static final long REAPER_INITIAL_DELAY_SEC = 120; //2 minutes to let system initialize fully before starting reaper
	private static final long BUCKET_CLEANER_INITIAL_DELAY_SEC = 145; //2 minutes to let system initialize fully before starting bucket cleaner
	private static final int REAPER_PERIOD_SEC = 60; //Run every minute
	
	private static final ScheduledExecutorService REAPER_EXECUTOR = Executors.newScheduledThreadPool(REAPER_POOL_SIZE);
	
	public ObjectStorageGateway() {}
	
	public static void checkPreconditions() throws EucalyptusCloudException, ExecutionException {
		LOG.debug("Checking ObjectStorageGateway preconditions");
		LOG.debug("ObjectStorageGateway Precondition check complete");
	}

	/**
	 * Configure 
	 */
	public static void configure() {
		synchronized(ObjectStorageGateway.class) {
			if(ospClient == null) {
				//TODO: zhill - wtf? Is this just priming the config? why is it unused.
				//lol yes. Lame, must be fixed.
				ObjectStorageGatewayInfo osgInfo = ObjectStorageGatewayInfo.getObjectStorageGatewayInfo();
				try {
					ospClient = ObjectStorageProviders.getInstance();
				} catch (Exception ex) {
					LOG.error (ex);
				}
			}
		}

		String limits = System.getProperty(ObjectStorageProperties.USAGE_LIMITS_PROPERTY);
		if(limits != null) {
			ObjectStorageProperties.shouldEnforceUsageLimits = Boolean.parseBoolean(limits);
		}
		try {
			ospClient.check();
		} catch(EucalyptusCloudException ex) {
			LOG.error("Error initializing walrus", ex);
			SystemUtil.shutdownWithError(ex.getMessage());
		}

		//Disable torrents
		//Tracker.initialize();
		if(System.getProperty("euca.virtualhosting.disable") != null) {
			ObjectStorageProperties.enableVirtualHosting = false;
		}
		try {
			if (ospClient != null) {
				//TODO: zhill - this seems wrong in check(), should be in enable() ?
				ospClient.start();
			}
		} catch(EucalyptusCloudException ex) {
			LOG.error("Error starting storage backend: " + ex);
		}		
	}

	public static void enable() throws EucalyptusCloudException {
		LOG.debug("Enabling ObjectStorageGateway");
		ospClient.enable();
		int intervalSec = REAPER_PERIOD_SEC;
		try {
		    intervalSec = ObjectStorageGatewayInfo.getObjectStorageGatewayInfo().getCleanupTaskIntervalSeconds();
		} catch(final Throwable f) {
		    LOG.error("Error getting configured reaper task interval. Using default: " + REAPER_PERIOD_SEC, f);
		}
		
		REAPER_EXECUTOR.scheduleAtFixedRate(new ObjectReaperTask(), REAPER_INITIAL_DELAY_SEC , intervalSec, TimeUnit.SECONDS);
		REAPER_EXECUTOR.scheduleAtFixedRate(new BucketCleanerTask(), BUCKET_CLEANER_INITIAL_DELAY_SEC , intervalSec, TimeUnit.SECONDS);
		LOG.debug("Enabling ObjectStorageGateway complete");
	}

	public static void disable() throws EucalyptusCloudException {
		LOG.debug("Disabling ObjectStorageGateway");
		ospClient.disable();

		//flush the data stream buffer, disconnect clients.
		streamDataMap.clear();
		LOG.debug("Disabling ObjectStorageGateway complete");
	}

	public static void check() throws EucalyptusCloudException {
		LOG.trace("Checking ObjectStorageGateway");
		ospClient.check();
		LOG.trace("Checking ObjectStorageGateway complete");
	}

	public static void stop() throws EucalyptusCloudException {
		LOG.debug("Checking ObjectStorageGateway preconditions");
		ospClient.stop();
		synchronized(ObjectStorageGateway.class) {
			ospClient = null;
		}
		Tracker.die();
		ObjectStorageProperties.shouldEnforceUsageLimits = true;
		ObjectStorageProperties.enableVirtualHosting = true;
		
		try {
		    List<Runnable> r = REAPER_EXECUTOR.shutdownNow();
		    LOG.info("Object reaper shutdown. Found " + r.size() + " pending tasks");
		} catch(final Throwable f) {
		    LOG.error("Error shutting down object-reaper.",f);
		}
		
		try {
		    ObjectManagers.getInstance().stop();
		} catch(Exception e) {
		    LOG.error("Error stopping object manager",e);
		}
		
		try {
		    BucketManagers.getInstance().stop();
		} catch(Exception e) {
		    LOG.error("Error stopping bucket manager",e);
		}

		//Be sure it's empty
		streamDataMap.clear();
		LOG.debug("Checking ObjectStorageGateway preconditions");
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#UpdateObjectStorageConfiguration(com.eucalyptus.objectstorage.msgs.UpdateObjectStorageConfigurationType)
	 */
	@Override
	public UpdateObjectStorageConfigurationResponseType updateObjectStorageConfiguration(UpdateObjectStorageConfigurationType request) throws EucalyptusCloudException {
		UpdateObjectStorageConfigurationResponseType reply = (UpdateObjectStorageConfigurationResponseType) request.getReply();
		if(ComponentIds.lookup(Eucalyptus.class).name( ).equals(request.getEffectiveUserId()))
			throw new AccessDeniedException("Only admin can change walrus properties.");
		if(request.getProperties() != null) {
			for(ComponentProperty prop : request.getProperties()) {
				LOG.info("ObjectStorage property: " + prop.getDisplayName() + " Qname: " + prop.getQualifiedName() + " Value: " + prop.getValue());
				try {
					ConfigurableProperty entry = PropertyDirectory.getPropertyEntry(prop.getQualifiedName());
					//type parser will correctly covert the value
					entry.setValue(prop.getValue());
				} catch (IllegalAccessException e) {
					LOG.error(e, e);
				}
			}
		}
		String name = request.getName();
		ospClient.check();
		return reply;
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetObjectStorageConfiguration(com.eucalyptus.objectstorage.msgs.GetObjectStorageConfigurationType)
	 */
	@Override
	public GetObjectStorageConfigurationResponseType getObjectStorageConfiguration(GetObjectStorageConfigurationType request) throws EucalyptusCloudException {
		GetObjectStorageConfigurationResponseType reply = (GetObjectStorageConfigurationResponseType) request.getReply();
		ConfigurableClass configurableClass = ObjectStorageGatewayInfo.class.getAnnotation(ConfigurableClass.class);
		if(configurableClass != null) {
			String prefix = configurableClass.root();
			reply.setProperties((ArrayList<ComponentProperty>) PropertyDirectory.getComponentPropertySet(prefix));
		}
		return reply;
	}

	/* PUT object */
	@ServiceOperation (async = false)
	public enum HandleFirstChunk implements Function<PutObjectType, Object> {
		INSTANCE;
			
		@Override
		public Object apply(final PutObjectType request) {
			logRequest(request);
			final ChannelBuffer b =  ChannelBuffers.dynamicBuffer();
			if(!streamDataMap.containsKey(request.getCorrelationId())) {
				byte[] firstChunk = request.getData();
				b.writeBytes(firstChunk);
				streamDataMap.put(request.getCorrelationId(), b);
			} else {
				//This should not happen. CorrelationIds should be unique for each request
				LOG.error("CorrelationId lookup in data map found duplicate. Unexpected error");
				return null;
			}
			
			Bucket bucket = null;
			try {
				User requestUser = Contexts.lookup().getUser();
				try {
					//Get the bucket metadata
					bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);				
				} catch(NoSuchElementException e) {
					throw new NoSuchBucketException(request.getBucket());
				} catch (Exception e) {
					LOG.error(e);
					throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
				}

				long newBucketSize = bucket.getBucketSize() == null ? 0 : bucket.getBucketSize();

				//TODO: this should be done in binding.
				if(Strings.isNullOrEmpty(request.getContentLength())) {
					//Not known. Content-Length is required by S3-spec.
					throw new MissingContentLengthException(request.getBucket() + "/" + request.getKey());
				}
				
				long objectSize = -1;
				try {					
					objectSize = Long.parseLong(request.getContentLength());					
					newBucketSize = bucket.getBucketSize() + objectSize;
				} catch(Exception e) {
					LOG.error("Could not parse content length into a long: " + request.getContentLength(), e);
					throw new MissingContentLengthException(request.getBucket() + "/" + request.getKey());
				}

				ObjectEntity objectEntity = new ObjectEntity(request.getBucket(), request.getKey(), null);
				
				//Generate a versionId if necessary based on versioning status of bucket
				String versionId = BucketManagers.getInstance().getVersionId(bucket);

				objectEntity.initializeForCreate(request.getBucket(), 
						request.getKey(), 
						versionId, 
						request.getCorrelationId(),
						objectSize,
						requestUser);
								
				if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, newBucketSize)) {
					//Construct and set the ACP properly, post Auth check so no self-auth can occur even accidentally
					AccessControlPolicy acp = new AccessControlPolicy();
					acp.setAccessControlList(request.getAccessControlList());
					acp = AclUtils.processNewResourcePolicy(requestUser, acp, bucket.getOwnerCanonicalId());
					
					objectEntity.setAcl(acp);
					
					final String fullObjectKey = objectEntity.getObjectUuid();
					request.setKey(fullObjectKey); //Ensure the backend uses the new full object name
					
					PutObjectResponseType response = ObjectManagers.getInstance().create(bucket, objectEntity,
							new CallableWithRollback<PutObjectResponseType,Boolean>() {
								@Override
								public PutObjectResponseType call() throws S3Exception, Exception {
									return ospClient.putObject(request, new ChannelBufferStreamingInputStream(b));
								}
								
								@Override
								public Boolean rollback(PutObjectResponseType arg) throws Exception {
									DeleteObjectType deleteRequest = new DeleteObjectType();
									deleteRequest.setBucket(request.getBucket());
									deleteRequest.setKey(fullObjectKey);
									DeleteObjectResponseType resp = ospClient.deleteObject(deleteRequest);
									if(resp != null) {
										return true;
									} else {
										return false;
									}
								}				
							}
						);					
					return response;		
				} else {
					throw new AccessDeniedException(request.getBucket());			
				}
				
			} catch(Exception ex) {
				//Convert since Function() can't throw exceptions
				//Should probably convert this to error response
				throw Exceptions.toUndeclared(ex);
			}
		}
	}

	@ServiceOperation (async = false)
	public enum HandleChunk implements Function<BaseDataChunk, Object> {
		INSTANCE;
		private static final int retryCount = 15;
		@Override
		public Object apply(BaseDataChunk chunk) {
			/*
			 * This works because chunks are delivered in-order through netty.
			 */
			String id = chunk.getCorrelationId();
			LOG.debug("Processing data chunk with id: " + id + " Last? " + chunk.isLast());
			try {
				ChannelBuffer writeBuffer = streamDataMap.get(id);
				//TODO: HACK! This should be handoff through a monitor.
				//This is required because there is a race between the first chunk
				//and the first data-only chunk.
				//Hacking around it to make progress on other ops, should revisit -ns
				
				//Proper solution is probably to aggregate the chunks to a ChannelBuffer in the Netty code, similar to HttpChunkAggregator
				// but passing the message along with reference to the ChannelBuffer rather than this map-based solution.
				for (int i=0; i < retryCount; ++i) {
					if (writeBuffer == null) {
						LOG.info("Stream Data Map is empty, retrying: " + (i + 1) + " of " + retryCount);
						Thread.sleep(100);
						writeBuffer = streamDataMap.get(id);
					}
				}
				writeBuffer.writeBytes(chunk.getContent());
			} catch (Exception e) {
				LOG.error(e, e);
			}
			return null;
		}	
	}
	
	/**
	 * A terse request logging function to log request entry at INFO level.
	 * @param request
	 */
	protected static <I extends ObjectStorageRequestType> void logRequest(I request) {
		StringBuilder canonicalLogEntry = new StringBuilder("osg handling request:" );
		try {			
			String accnt = null;
			String src = null;
			try {
				Context ctx = Contexts.lookup(request.getCorrelationId());
				accnt = ctx.getAccount().getAccountNumber();
				src = ctx.getRemoteAddress().getHostAddress();
			} catch(Exception e) {
				LOG.warn("Failed context lookup by correlation Id: " + request.getCorrelationId());
			} finally {
				if(Strings.isNullOrEmpty(accnt)) {
					accnt = "unknown";
				}
				if(Strings.isNullOrEmpty(src)) {
					src = "unknown";
				}
			}

			canonicalLogEntry.append(" Operation: " + request.getClass().getSimpleName());
			canonicalLogEntry.append(" Account: " + accnt);
			canonicalLogEntry.append(" Src Ip: " + src);		
			canonicalLogEntry.append(" Bucket: " + request.getBucket());
			canonicalLogEntry.append(" Object: " + request.getKey());
			if(request instanceof GetObjectType) {
				canonicalLogEntry.append(" VersionId: " + ((GetObjectType)request).getVersionId());
			} else if(request instanceof PutObjectType) {
				canonicalLogEntry.append(" ContentMD5: " + ((PutObjectType)request).getContentMD5());
			}		
			LOG.info(canonicalLogEntry.toString());
		} catch(Exception e) {
			LOG.warn("Problem formatting request log entry. Incomplete entry: " + canonicalLogEntry == null ? "null" : canonicalLogEntry.toString(), e);
		}		
	}
		
	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#HeadBucket(com.eucalyptus.objectstorage.msgs.HeadBucketType)
	 */
	@Override
	public HeadBucketResponseType headBucket(HeadBucketType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		try {	
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);			
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Internal error finding bucket " + request.getBucket(), e);
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			HeadBucketResponseType reply = (HeadBucketResponseType) request.getReply();
			reply.setBucket(bucket.getBucketName());
			reply.setStatus(HttpResponseStatus.OK);
			reply.setStatusMessage("OK");
			reply.setTimestamp(new Date());
			return reply;
		} else {
			throw new AccessDeniedException(request.getBucket());			
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#CreateBucket(com.eucalyptus.objectstorage.msgs.CreateBucketType)
	 */
	@Override
	public CreateBucketResponseType createBucket(final CreateBucketType request) throws EucalyptusCloudException {
		logRequest(request);
		
		long bucketCount = 0;
		User requestUser = null;
		try {
			requestUser = Contexts.lookup(request.getCorrelationId()).getUser();
			bucketCount = BucketManagers.getInstance().countByUser(requestUser.getUserId(), false, null);	
		} catch (NoSuchContextException e) {
			LOG.error("Error finding context to lookup canonical Id of user", e);
			throw new InternalErrorException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Failed getting bucket count for user " + requestUser.getUserId());
			//Don't fail the operation, the count may not be important
			bucketCount = 0;
		}
		
		//Fake entity for auth check, need the name to allow checks against
		//TODO: refactor the bucket manager to make this easier
		final Bucket fakeBucket = new Bucket(request.getBucket());
		try {
			fakeBucket.setOwnerCanonicalId(requestUser.getAccount().getCanonicalId());
		} catch (AuthException e) {
			LOG.error("No account found for user: " + requestUser.getUserId());
			throw new AccountProblemException(requestUser.getUserId());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, fakeBucket, null, bucketCount + 1)) {
			try {
				//Check the validity of the bucket name.				
				if (!BucketManagers.getInstance().checkBucketName(request.getBucket())) {
					throw new InvalidBucketNameException(request.getBucket());
				}

				/* 
				 * This is a secondary check, independent to the iam quota check, based on the configured max bucket count property.
				 * The count does not include "hidden" buckets for snapshots etc since the user has no direct control of those via the s3 endpoint 
				 */
				if (ObjectStorageProperties.shouldEnforceUsageLimits && 
						!Contexts.lookup().hasAdministrativePrivileges() &&					
						BucketManagers.getInstance().countByAccount(requestUser.getAccount().getCanonicalId(), true, null) >= ObjectStorageGatewayInfo.getObjectStorageGatewayInfo().getStorageMaxBucketsPerAccount()) {
					throw new TooManyBucketsException(request.getBucket());					
				}

				AccessControlPolicy tmpPolicy = new AccessControlPolicy();
				tmpPolicy.setAccessControlList(request.getAccessControlList());
				AccessControlPolicy acPolicy = AclUtils.processNewResourcePolicy(requestUser, tmpPolicy , null);				
				String aclString = S3AccessControlledEntity.marshallACPToString(acPolicy);
				if(aclString == null) {
					LOG.error("Unexpectedly got null for acl string. Cannot complete bucket creation with null acl");
					throw new InternalErrorException(request.getBucket());
				}
						
				return BucketManagers.getInstance().create(request.getBucket(),
						requestUser,
						aclString,
						request.getLocationConstraint(),
						new CallableWithRollback<CreateBucketResponseType, Boolean>() {
					public CreateBucketResponseType call() throws Exception {
						return ospClient.createBucket(request);
					}
					
					public Boolean rollback(CreateBucketResponseType arg) throws Exception {
						DeleteBucketType deleteRequest = new DeleteBucketType();
						deleteRequest.setBucket(arg.getBucket());					
						try {
							DeleteBucketResponseType response = ospClient.deleteBucket(deleteRequest);
							return response.get_return();
						} catch(Exception e) {
							LOG.error("Rollback (deletebucket) for createbucket " + arg.getBucket() + " failed",e);
							return false;
						}
					}
				});
			} catch(S3Exception e) {
				LOG.error("Error creating bucket " + request.getBucket(), e);
				throw e;			
			} catch(Exception e) {
				LOG.error("Unknown exception caused failure of CreateBucket for bucket " + request.getBucket(), e);
				throw new InternalErrorException(request.getBucket());
			}
		} else {
			throw new AccessDeniedException(request.getBucket());			
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#DeleteBucket(com.eucalyptus.objectstorage.msgs.DeleteBucketType)
	 */
	@Override
	public DeleteBucketResponseType deleteBucket(final DeleteBucketType request) throws EucalyptusCloudException {
		logRequest(request);
		
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			//Ok, bucket not found.
			bucket = null;
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		}
		
		if(bucket == null) {
			//Bucket does not exist, so return success. This is per s3-spec.
			DeleteBucketResponseType reply = (DeleteBucketResponseType) request.getReply();
			reply.setStatus(HttpResponseStatus.NO_CONTENT);
			reply.setStatusMessage("No Content");
			return reply;
		} else {
			if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
				long objectCount = 0;
				try {
					objectCount = ObjectManagers.getInstance().count(bucket);
				} catch(Exception e) {
					//Bail if we can't confirm bucket is empty.
					LOG.error("Error fetching object count for bucket " + bucket.getBucketName());
					throw new InternalErrorException(bucket.getBucketName());
				}
				
				if(objectCount > 0) {
					throw new BucketNotEmptyException(bucket.getBucketName());
				} else {
					try {
						return BucketManagers.getInstance().delete(bucket, new CallableWithRollback<DeleteBucketResponseType, Boolean>() {
							
							@Override
							public DeleteBucketResponseType call() throws Exception {
								return ospClient.deleteBucket(request);
							}
							
							@Override
							public Boolean rollback(DeleteBucketResponseType arg)
									throws Exception {
								//No rollback for bucket deletion
								return true;
							}					
						});
					} catch(Exception e) {
						LOG.error("Transaction error deleting bucket " + request.getBucket(),e);
						throw new InternalErrorException(request.getBucket());
					}
				}				
			} else {
				throw new AccessDeniedException(request.getBucket());			
			}
		}
	}
	
	protected static ListAllMyBucketsList generateBucketListing(List<Bucket> buckets) {
		ListAllMyBucketsList bucketList = new ListAllMyBucketsList();
		bucketList.setBuckets(new ArrayList<BucketListEntry>());
		for(Bucket b : buckets ) {
			bucketList.getBuckets().add(b.toBucketListEntry());
		}
		return bucketList;
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#ListAllMyBuckets(com.eucalyptus.objectstorage.msgs.ListAllMyBucketsType)
	 */
	@Override
	public ListAllMyBucketsResponseType listAllMyBuckets(ListAllMyBucketsType request) throws EucalyptusCloudException {
		logRequest(request);
		
		//Create a fake bucket record just for IAM verification. The IAM policy is only valid for arn:s3:* so empty should match
		/*
		 * ListAllMyBuckets is a weird authentication for IAM because it is technically a bucket operation, but the request
		 * is not against a specific bucket and the account admin cannot limit listallbuckets output on a per-bucket basis.
		 * The only valid resource to grant s3:ListAllMyBuckets to is '*'.
		 * 
		 * This sets up a fake bucket so that the ACL checks and basic ownership checks can be passed, leaving just the IAM permission
		 * check.
		 */
		Bucket fakeBucket = new Bucket();
		fakeBucket.setBucketName("fakebucket"); // '*' should match this
		fakeBucket.setOwnerCanonicalId(Contexts.lookup().getAccount().getCanonicalId()); // make requestor the owner of fake bucket
		request.setBucket(fakeBucket.getBucketName());
				
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, fakeBucket, null, 0)) {
			ListAllMyBucketsResponseType response = (ListAllMyBucketsResponseType) request.getReply();
			/*
			 * This is a strictly metadata operation, no backend is hit. The sync of metadata in OSG to backend is done elsewhere asynchronously.
			 */
			Account accnt = null;
			try {
				accnt = Contexts.lookup(request.getCorrelationId()).getAccount();
				if(accnt == null) {
					throw new NoSuchContextException();
				}
			} catch (NoSuchContextException e) {
				try {
					accnt = Accounts.lookupUserByAccessKeyId(request.getAccessKeyID()).getAccount();
				} catch(AuthException ex) {
					LOG.error("Could not retrieve canonicalId for user with accessKey: " + request.getAccessKeyID());
					throw new InternalErrorException();
				}
			}
			try {
				List<Bucket> listing = BucketManagers.getInstance().list(accnt.getCanonicalId(), false, null);
				response.setBucketList(generateBucketListing(listing));
				response.setOwner(AclUtils.buildCanonicalUser(accnt));
				return response;
			} catch(Exception e) {
				throw new InternalErrorException();
			}
		} else {
			AccessDeniedException ex = new AccessDeniedException();
			ex.setResource("ListAllMyBuckets");
			ex.setMessage("Insufficient permissions to list buckets. Check with your account administrator");
			ex.setResourceType("Service");
			throw ex;
		}		
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetBucketAccessControlPolicy(com.eucalyptus.objectstorage.msgs.GetBucketAccessControlPolicyType)
	 */
	@Override
	public GetBucketAccessControlPolicyResponseType getBucketAccessControlPolicy(GetBucketAccessControlPolicyType request) throws EucalyptusCloudException
	{
		logRequest(request);
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Error getting metadata for object " + request.getBucket() + " " + request.getKey());
			throw new InternalErrorException(request.getBucket() + "/?acl");
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			//Get the listing from the back-end and copy results in.
			GetBucketAccessControlPolicyResponseType reply = (GetBucketAccessControlPolicyResponseType)request.getReply();
			reply.setBucket(request.getBucket());
			try {
				reply.setAccessControlPolicy(bucket.getAccessControlPolicy());
			} catch(Exception e) {
				throw new InternalErrorException(request.getBucket() + "/?acl");
			}
			return reply;
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#PostObject(com.eucalyptus.objectstorage.msgs.PostObjectType)
	 */
	@Override
	public PostObjectResponseType postObject (PostObjectType request) throws EucalyptusCloudException {
		logRequest(request);
		throw new NotImplementedException("POST object");
		//return ospClient.postObject(request);
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#DeleteObject(com.eucalyptus.objectstorage.msgs.DeleteObjectType)
	 */
	@Override
	public DeleteObjectResponseType deleteObject (final DeleteObjectType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		ObjectEntity objectEntity = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Error getting bucket metadata for bucket " + request.getBucket());
			throw new InternalErrorException(request.getBucket());
		}
		
		try {
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), null);		
		} catch(NoSuchElementException e) {
			//Nothing to do, object doesn't exist. Return 204 per S3 spec
			DeleteObjectResponseType reply = (DeleteObjectResponseType) request.getReply();
			reply.setStatus(HttpResponseStatus.NO_CONTENT);
			reply.setStatusMessage("No Content");
			return reply;
		} catch(Exception e) {
			LOG.error("Error getting bucket metadata for bucket " + request.getBucket());
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, 0)) {
			//Get the listing from the back-end and copy results in.
			try {
				ObjectManagers.getInstance().delete(bucket, objectEntity,
						new CallableWithRollback<DeleteObjectResponseType,Boolean>() {
					public DeleteObjectResponseType call() throws S3Exception, Exception {
						return ospClient.deleteObject(request);
					}
					
					public Boolean rollback(DeleteObjectResponseType arg) throws S3Exception, Exception {
						//Can't roll-back a delete
						return true;
					}
				});
				DeleteObjectResponseType reply = (DeleteObjectResponseType) request.getReply();
				reply.setStatus(HttpResponseStatus.NO_CONTENT);
				reply.setStatusMessage("No Content");
				return reply;
			} catch (Exception e) {
				LOG.error("Transaction error during delete object: " + request.getBucket() + "/" + request.getKey(), e);
				throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
			}
		} else {
			throw new AccessDeniedException(request.getBucket() + "/" + request.getKey());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#ListBucket(com.eucalyptus.objectstorage.msgs.ListBucketType)
	 */
	@Override
	public ListBucketResponseType listBucket(ListBucketType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket listBucket = null;
		try {
			listBucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			//bucket not found
			throw new NoSuchBucketException(request.getBucket());		
		} catch(Exception e) {
			LOG.error("Error getting bucket metadata for bucket " + request.getBucket());
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, listBucket, null, 0)) {
			//Get the listing from the back-end and copy results in.				
			//return ospClient.listBucket(request);
			ListBucketResponseType reply = (ListBucketResponseType) request.getReply();				
			int maxKeys = 1000;			
			try {
				if(!Strings.isNullOrEmpty(request.getMaxKeys())) {
					maxKeys = Integer.parseInt(request.getMaxKeys());
				}
			} catch(NumberFormatException e) {
				LOG.error("Failed to parse maxKeys from request properly: " + request.getMaxKeys(), e);
				throw new InvalidArgumentException("MaxKeys");
			}
			reply.setMaxKeys(maxKeys);
			reply.setName(request.getBucket());				
			reply.setDelimiter(request.getDelimiter());
			reply.setMarker(request.getMarker());
			reply.setPrefix(request.getPrefix());								
			reply.setIsTruncated(false);			
			
			PaginatedResult<ObjectEntity> result = null;
			try {
				result = ObjectManagers.getInstance().listPaginated(listBucket, maxKeys, request.getPrefix(), request.getDelimiter(), request.getMarker());									
			} catch(Exception e) {
				LOG.error("Error getting object listing for bucket: " + request.getBucket(), e);
				throw new InternalErrorException(request.getBucket());
			}
			
			if(result != null) {
				reply.setContents(new ArrayList<ListEntry>());
				
				for(ObjectEntity obj : result.getEntityList()){
					reply.getContents().add(obj.toListEntry());
				}
				
				for(String s : result.getCommonPrefixes()) {
					reply.getCommonPrefixes().add(new PrefixEntry(s));
				}
				reply.setIsTruncated(result.isTruncated);
				if(result.isTruncated) {
					if(	result.getLastEntry() instanceof ObjectEntity) {					
						reply.setNextMarker(((ObjectEntity)result.getLastEntry()).getObjectKey());
					} else {
						reply.setNextMarker(result.getLastEntry().toString());
					}
				}
			} else {
				//Do nothing
//				reply.setContents(new ArrayList<ListEntry>());
			}
			
			return reply;
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetObjectAccessControlPolicy(com.eucalyptus.objectstorage.msgs.GetObjectAccessControlPolicyType)
	 */
	@Override
	public GetObjectAccessControlPolicyResponseType getObjectAccessControlPolicy(GetObjectAccessControlPolicyType request) throws EucalyptusCloudException {
		logRequest(request);
		ObjectEntity objectEntity = null;
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Error getting metadata for object " + request.getBucket() + " " + request.getKey());
			throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
		}
		
		try {
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), request.getVersionId());
		} catch(NoSuchElementException e) {
			throw new NoSuchKeyException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Error getting metadata for object " + request.getBucket() + " " + request.getKey());
			throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
		} 
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, null, objectEntity, 0)) {
			//Get the listing from the back-end and copy results in.
			GetObjectAccessControlPolicyResponseType reply = (GetObjectAccessControlPolicyResponseType)request.getReply();
			reply.setBucket(request.getBucket());
			try {
				reply.setAccessControlPolicy(objectEntity.getAccessControlPolicy());
			} catch(Exception e) {
				throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
			}
			return reply;
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#SetRESTBucketAccessControlPolicy(com.eucalyptus.objectstorage.msgs.SetRESTBucketAccessControlPolicyType)
	 */
	@Override
	public SetRESTBucketAccessControlPolicyResponseType setRESTBucketAccessControlPolicy(final SetRESTBucketAccessControlPolicyType request) throws EucalyptusCloudException {
		logRequest(request);
		
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Error getting metadata for object " + request.getBucket() + " " + request.getKey());
			throw new InternalErrorException(request.getBucket() + "/?acl");
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			final String bucketOwnerCanonicalId = bucket.getOwnerCanonicalId();
			String aclString = null;						
			if(request.getAccessControlPolicy() == null || request.getAccessControlPolicy().getAccessControlList() == null) {
				//Can't set to null
				throw new MalformedACLErrorException(request.getBucket() + "/" + request.getKey() + "?acl");
			} else {
				//Expand the acl first
				request.getAccessControlPolicy().setAccessControlList(AclUtils.expandCannedAcl(request.getAccessControlPolicy().getAccessControlList(), bucketOwnerCanonicalId, null));						
				if(request.getAccessControlPolicy() == null || request.getAccessControlPolicy().getAccessControlList() == null) {
					//Something happened in acl expansion.
					LOG.error("Cannot put ACL that does not exist in request");
					throw new InternalErrorException(request.getBucket() + "?acl");
				} else {
					//Add in the owner entry if not present
					if(request.getAccessControlPolicy().getOwner() == null ) {
						request.getAccessControlPolicy().setOwner(new CanonicalUser(bucketOwnerCanonicalId,""));
					}
				}
				
				//Marshal into a string
				aclString = S3AccessControlledEntity.marshallACPToString(request.getAccessControlPolicy());
				if(Strings.isNullOrEmpty(aclString)) {
					throw new MalformedACLErrorException(request.getBucket() + "?acl");
				}
			}			
			try {
				return BucketManagers.getInstance().setAcp(bucket, aclString,
						new CallableWithRollback<SetRESTBucketAccessControlPolicyResponseType, Boolean>() {
					
					@Override
					public SetRESTBucketAccessControlPolicyResponseType call() throws S3Exception, Exception {
						return ospClient.setRESTBucketAccessControlPolicy(request);
					}
					
					@Override
					public Boolean rollback(SetRESTBucketAccessControlPolicyResponseType arg) throws S3Exception, Exception {
						//TODO: could preserve the old ACP and restore it here for the backend
						return true;
					}
				});
			} catch(Exception e) {
				LOG.error("Transaction error updating bucket ACL for bucket " + request.getBucket(),e);
				throw new InternalErrorException(request.getBucket() + "?acl");
			}
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#SetRESTObjectAccessControlPolicy(com.eucalyptus.objectstorage.msgs.SetRESTObjectAccessControlPolicyType)
	 */
	@Override
	public SetRESTObjectAccessControlPolicyResponseType setRESTObjectAccessControlPolicy(final SetRESTObjectAccessControlPolicyType request) throws EucalyptusCloudException {
		logRequest(request);
		ObjectEntity objectEntity = null;
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), request.getVersionId());		
		} catch(NoSuchElementException e) {
			if(objectEntity == null) {
				throw new NoSuchKeyException(request.getBucket() + "/" + request.getKey() + "?versionId=" + request.getVersionId());
			} else {
				throw new NoSuchBucketException(request.getBucket());
			}			
		} catch(Exception e) {
			LOG.error("Error getting metadata for object " + request.getBucket() + " " + request.getKey());
			throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, 0)) {
			
			SetRESTObjectAccessControlPolicyResponseType reply = (SetRESTObjectAccessControlPolicyResponseType)request.getReply();
			final String bucketOwnerId = bucket.getOwnerCanonicalId();
			final String objectOwnerId = objectEntity.getOwnerCanonicalId();
			try {
				String aclString = null;
				if(request.getAccessControlPolicy() == null || request.getAccessControlPolicy().getAccessControlList() == null) {
					//Can't set to null
					throw new MalformedACLErrorException(request.getBucket() + "/" + request.getKey() + "?acl");
				} else {
					//Expand the acl first
					request.getAccessControlPolicy().setAccessControlList(AclUtils.expandCannedAcl(request.getAccessControlPolicy().getAccessControlList(), bucketOwnerId, objectOwnerId));						
					if(request.getAccessControlPolicy() == null || request.getAccessControlPolicy().getAccessControlList() == null) {
						//Something happened in acl expansion.
						LOG.error("Cannot put ACL that does not exist in request");
						throw new InternalErrorException(request.getBucket() + "/" + request.getKey() + "?acl");
					} else {
						//Add in the owner entry if not present
						if(request.getAccessControlPolicy().getOwner() == null ) {
							request.getAccessControlPolicy().setOwner(new CanonicalUser(objectOwnerId,""));
						}
					}
					
					//Marshal into a string
					aclString = S3AccessControlledEntity.marshallACPToString(request.getAccessControlPolicy());
					if(Strings.isNullOrEmpty(aclString)) {
						throw new MalformedACLErrorException(request.getBucket() + "/" + request.getKey() + "?acl");
					}
				}
				
				//Get the listing from the back-end and copy results in.
				return ObjectManagers.getInstance().setAcp(objectEntity, request.getAccessControlPolicy(), 
						new CallableWithRollback<SetRESTObjectAccessControlPolicyResponseType, Boolean>() {
					
					@Override
					public SetRESTObjectAccessControlPolicyResponseType call()
							throws S3Exception, Exception {
						return ospClient.setRESTObjectAccessControlPolicy(request);
					}
					
					@Override
					public Boolean rollback(SetRESTObjectAccessControlPolicyResponseType arg)
									throws S3Exception, Exception {
						return true;
					}							
				});
			} catch(Exception e) {
				LOG.error("Internal error during PUT object?acl for object " + request.getBucket() + "/" + request.getKey(), e);
				throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
			}
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetObject(com.eucalyptus.objectstorage.msgs.GetObjectType)
	 */
	@Override
	public GetObjectResponseType getObject(GetObjectType request) throws EucalyptusCloudException {
		logRequest(request);
		ObjectEntity objectEntity = null;
		Bucket bucket = null;
		try {
			//Handle the pass-through
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), request.getVersionId());
		} catch(NoSuchElementException e) {
			throw new NoSuchKeyException(request.getBucket() + "/" + request.getKey() + "?versionId=" + request.getVersionId());
		} catch (Exception e) {
			if(e.getCause() instanceof NoSuchElementException) {
				//Just in case
				throw new NoSuchKeyException(request.getBucket() + "/" + request.getKey() + "?versionId=" + request.getVersionId());
			}
			LOG.error(e);
			throw new InternalErrorException(request.getBucket() + "/" + request.getKey() + " , version= " +  request.getVersionId());
		}
		
		//TODO: make sure to handle getVersion case on auth. May need different operation to handle that case
		// since it is a different IAM check
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, 0)) {
			request.setKey(objectEntity.getObjectUuid());
			ospClient.getObject(request);
			//ObjectGetter getter = new ObjectGetter(request);
			//Threads.lookup(ObjectStorage.class, ObjectStorageGateway.ObjectGetter.class).limitTo(1).submit(getter);
			return null;
		} else {
			throw new AccessDeniedException(request.getBucket() + "/" + request.getKey() + "?versionId=" + request.getVersionId());
		}	
	}

	private class ObjectGetter implements Runnable {
		GetObjectType request;
		public ObjectGetter(GetObjectType request) {
			this.request = request;
		}
		@Override
		public void run() {
			try {
				ospClient.getObject(request);
			} catch (Exception ex) {
				LOG.error(ex, ex);
			}
		}

	}
	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetObjectExtended(com.eucalyptus.objectstorage.msgs.GetObjectExtendedType)
	 */
	@Override
	public GetObjectExtendedResponseType getObjectExtended(GetObjectExtendedType request) throws EucalyptusCloudException {
		logRequest(request);
		ObjectEntity objectEntity = null;
		Bucket bucket = null;
		User requestUser = null;
		try {
			requestUser = Contexts.lookup().getUser();
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			throw new InternalErrorException();
		}		
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, 0)) {				
			ospClient.getObjectExtended(request);
			//return ospClient.getObjectExtended(request);
			return null;
		} else {
			throw new AccessDeniedException(request.getBucket() + "/" + request.getKey());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetBucketLocation(com.eucalyptus.objectstorage.msgs.GetBucketLocationType)
	 */
	@Override
	public GetBucketLocationResponseType getBucketLocation(GetBucketLocationType request) throws EucalyptusCloudException {
		logRequest(request);		
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());		
		}
			
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			GetBucketLocationResponseType reply = (GetBucketLocationResponseType) request.getReply();
			reply.setLocationConstraint(bucket.getLocation());
			reply.setBucket(request.getBucket());
			return reply;
		} else {		
			throw new AccessDeniedException(request.getBucket());			
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#CopyObject(com.eucalyptus.objectstorage.msgs.CopyObjectType)
	 */
	@Override
	public CopyObjectResponseType copyObject(CopyObjectType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);		
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		}
		
		ObjectEntity objectEntity = null;
		try {
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), null);
		} catch(NoSuchElementException e) {
			throw new NoSuchKeyException(request.getBucket() + "/" + request.getKey());
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, 0)) {
			//TODO: implement the db changes here.
			throw new NotImplementedException("CopyObject");
			//return ospClient.copyObject(request);
		} else {		
			throw new AccessDeniedException(request.getBucket());			
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetBucketLoggingStatus(com.eucalyptus.objectstorage.msgs.GetBucketLoggingStatusType)
	 */
	@Override
	public GetBucketLoggingStatusResponseType getBucketLoggingStatus(GetBucketLoggingStatusType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			GetBucketLoggingStatusResponseType reply = (GetBucketLoggingStatusResponseType) request.getReply();
			LoggingEnabled loggingConfig = new LoggingEnabled();
			if(bucket.getLoggingEnabled()) {
				Bucket targetBucket = null;
				try {
					targetBucket = BucketManagers.getInstance().get(bucket.getTargetBucket(), false, null);
				} catch(Exception e) {
					LOG.error("Error locating target bucket info for bucket " + request.getBucket() + " on target bucket " + bucket.getTargetBucket(), e);
				}
				
				TargetGrants grants = new TargetGrants();
				try {
					grants.setGrants(targetBucket.getAccessControlPolicy().getAccessControlList().getGrants());
				} catch(Exception e) {
					LOG.error("Error populating target grants for bucket " + request.getBucket() + " for target " + targetBucket.getBucketName(),e);
					grants.setGrants(new ArrayList<Grant>());
				}						
				loggingConfig.setTargetBucket(bucket.getTargetBucket());
				loggingConfig.setTargetPrefix(bucket.getTargetPrefix());
				loggingConfig.setTargetGrants(grants);
				reply.setLoggingEnabled(loggingConfig);
			} else {
				//Logging not enabled
				reply.setLoggingEnabled(null);
			}
			
			return reply;
		} else {		
			throw new AccessDeniedException(request.getBucket());			
		}
	}
	
	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#SetBucketLoggingStatus(com.eucalyptus.objectstorage.msgs.SetBucketLoggingStatusType)
	 */
	@Override
	public SetBucketLoggingStatusResponseType setBucketLoggingStatus(final SetBucketLoggingStatusType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			//TODO: zhill -- add support for this. Not implemented for the tech preview
			throw new NotImplementedException("PUT ?logging");
		} else {		
			throw new AccessDeniedException(request.getBucket());			
		}		
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#GetBucketVersioningStatus(com.eucalyptus.objectstorage.msgs.GetBucketVersioningStatusType)
	 */
	@Override
	public GetBucketVersioningStatusResponseType getBucketVersioningStatus(GetBucketVersioningStatusType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		
		}

		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			//Metadata only, don't hit the backend
			GetBucketVersioningStatusResponseType reply = (GetBucketVersioningStatusResponseType)request.getReply();
			reply.setVersioningStatus(bucket.getVersioning());
			reply.setBucket(request.getBucket());
			return reply;
		} else {		
			throw new AccessDeniedException(request.getBucket());			
		}	
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#SetBucketVersioningStatus(com.eucalyptus.objectstorage.msgs.SetBucketVersioningStatusType)
	 */
	@Override
	public SetBucketVersioningStatusResponseType setBucketVersioningStatus(final SetBucketVersioningStatusType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		}catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());		
		} catch(Exception e) {
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, null, 0)) {
			throw new NotImplementedException("PUT ?versioning");
			/*
			try {
				final String oldState = bucket.getVersioning();
				final String bucketName = request.getBucket();
				final VersioningStatus newState = VersioningStatus.valueOf(request.getVersioningStatus());
				
				return BucketManagers.getInstance().setVersioning(bucket, 
						newState, 
						new CallableWithRollback<SetBucketVersioningStatusResponseType, Boolean>() {
					
					@Override
					public SetBucketVersioningStatusResponseType call() throws Exception {
						return ospClient.setBucketVersioningStatus(request);
					}
					
						@Override
						public Boolean rollback(SetBucketVersioningStatusResponseType arg) throws Exception {
							SetBucketVersioningStatusType revertRequest = new SetBucketVersioningStatusType();
							revertRequest.setVersioningStatus(oldState);
							revertRequest.setBucket(bucketName);
							try {
								SetBucketVersioningStatusResponseType response = ospClient.setBucketVersioningStatus(revertRequest);							
								if(response != null && response.get_return()) {
									return true;
								}
							} catch(Exception e) {
								LOG.error("Error invoking bucket versioning state rollback from " + request.getVersioningStatus() + " to " + oldState, e);
								return false;
							}
							
							return false;
						}					
				});
				
			} catch(Exception e) {
				LOG.error("Transaction error deleting bucket " + request.getBucket(),e);
				throw new InternalErrorException(request.getBucket());
			}
			*/
		} else {		
			throw new AccessDeniedException(request.getBucket());			
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#ListVersions(com.eucalyptus.objectstorage.msgs.ListVersionsType)
	 */
	@Override
	public ListVersionsResponseType listVersions(ListVersionsType request) throws EucalyptusCloudException {
		logRequest(request);
		Bucket listBucket = null;
		try {
			listBucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
		} catch(NoSuchElementException e) {
			throw new NoSuchBucketException(request.getBucket());
		} catch(Exception e) {
			LOG.error("Error getting bucket metadata for bucket " + request.getBucket());
			throw new InternalErrorException(request.getBucket());
		}		
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, listBucket, null, 0)) {
			//TODO: make almost the same as listBucket
			//Get the listing from the back-end and copy results in.
			//return ospClient.listVersions(request);
			throw new NotImplementedException("GET ?versions");
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/* (non-Javadoc)
	 * @see com.eucalyptus.objectstorage.ObjectStorageService#DeleteVersion(com.eucalyptus.objectstorage.msgs.DeleteVersionType)
	 */
	@Override
	public DeleteVersionResponseType deleteVersion(final DeleteVersionType request) throws EucalyptusCloudException {
		logRequest(request);
		ObjectEntity objectEntity = null;
		Bucket bucket = null;
		try {
			bucket = BucketManagers.getInstance().get(request.getBucket(), false, null);
			objectEntity = ObjectManagers.getInstance().get(bucket, request.getKey(), request.getVersionid());			
		} catch(NoSuchElementException e) {
			throw new NoSuchVersionException(request.getBucket() + "/" + request.getKey() + "?versionId=" + request.getVersionid());
		} catch(Exception e) {
			LOG.error("Error getting metadata for delete version operation on " + request.getBucket() + "/" + request.getKey() + "?version=" + request.getVersionid());
			throw new InternalErrorException(request.getBucket());
		}
		
		if(OSGAuthorizationHandler.getInstance().operationAllowed(request, bucket, objectEntity, 0)) {
			throw new NotImplementedException("DELETE ?version");

			//Get the listing from the back-end and copy results in.
			/*try {
				final DeleteVersionType backendRequest = (DeleteVersionType)request.regardingUserRequest(request);
				backendRequest.setBucket(request.getBucket());
				backendRequest.setKey(objectEntity.getObjectUuid());
				backendRequest.setVersionid(request.getVersionid());
				
				ObjectManagers.getInstance().delete(objectEntity, new CallableWithRollback<DeleteVersionResponseType, Boolean>() {
					@Override
					public DeleteVersionResponseType call() throws S3Exception,
					Exception {
						//TODO: need to use a different request to handle the internal key
						return ospClient.deleteVersion(request);
					}
					
					@Override
					public Boolean rollback(DeleteVersionResponseType arg)
							throws S3Exception, Exception {
						// TODO Auto-generated method stub
						return null;
					}					
				});
				
				DeleteVersionResponseType reply = (DeleteVersionResponseType)request.getReply();
				return reply;				
			} catch(Exception e) {
				LOG.error("Error deleting",e);
				throw new InternalErrorException(request.getBucket() + "/" + request.getKey());
			}*/
		} else {
			throw new AccessDeniedException(request.getBucket());
		}
	}

	/**
	 * Returns an IP for an enabled OSG if the bucket exists. Throws exception if not.
	 * For multiple OSG, a random IP is returned from the set of ENABLED OSG IPs.
	 * @param bucket
	 * @return
	 * @throws EucalyptusCloudException
	 */
	public static InetAddress getBucketIp(String bucket) throws EucalyptusCloudException {
	    try {		
	    	if(BucketManagers.getInstance().exists(bucket, null)) {
	    		ServiceConfiguration[] osgs =  Iterables.toArray(Topology.lookupMany(ObjectStorage.class), ServiceConfiguration.class);
	    		if(osgs != null && osgs.length > 0) {			
	    			return osgs[rand.nextInt(osgs.length - 1)].getInetAddress();
	    		}
	    	}
	    	throw new NoSuchElementException(bucket);
	    } catch (Exception ex) {
	    	throw new EucalyptusCloudException(ex);
	    }	    	    
	}

}
