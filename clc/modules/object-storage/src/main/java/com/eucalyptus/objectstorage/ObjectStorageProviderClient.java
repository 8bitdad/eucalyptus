package com.eucalyptus.objectstorage;

import java.io.InputStream;

import com.eucalyptus.objectstorage.msgs.AddObjectResponseType;
import com.eucalyptus.objectstorage.msgs.AddObjectType;
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
import com.eucalyptus.objectstorage.msgs.GetObjectType;
import com.eucalyptus.objectstorage.msgs.HeadBucketResponseType;
import com.eucalyptus.objectstorage.msgs.HeadBucketType;
import com.eucalyptus.objectstorage.msgs.ListAllMyBucketsResponseType;
import com.eucalyptus.objectstorage.msgs.ListAllMyBucketsType;
import com.eucalyptus.objectstorage.msgs.ListBucketResponseType;
import com.eucalyptus.objectstorage.msgs.ListBucketType;
import com.eucalyptus.objectstorage.msgs.ListVersionsResponseType;
import com.eucalyptus.objectstorage.msgs.ListVersionsType;
import com.eucalyptus.objectstorage.msgs.PostObjectResponseType;
import com.eucalyptus.objectstorage.msgs.PostObjectType;
import com.eucalyptus.objectstorage.msgs.PutObjectInlineResponseType;
import com.eucalyptus.objectstorage.msgs.PutObjectInlineType;
import com.eucalyptus.objectstorage.msgs.PutObjectResponseType;
import com.eucalyptus.objectstorage.msgs.PutObjectType;
import com.eucalyptus.objectstorage.msgs.SetBucketAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.SetBucketAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.SetBucketLoggingStatusResponseType;
import com.eucalyptus.objectstorage.msgs.SetBucketLoggingStatusType;
import com.eucalyptus.objectstorage.msgs.SetBucketVersioningStatusResponseType;
import com.eucalyptus.objectstorage.msgs.SetBucketVersioningStatusType;
import com.eucalyptus.objectstorage.msgs.SetObjectAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.SetObjectAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.SetRESTBucketAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.SetRESTBucketAccessControlPolicyType;
import com.eucalyptus.objectstorage.msgs.SetRESTObjectAccessControlPolicyResponseType;
import com.eucalyptus.objectstorage.msgs.SetRESTObjectAccessControlPolicyType;
import com.eucalyptus.util.EucalyptusCloudException;

/**
 * Class that any ObjectStorageProvider client implementation must extend.
 * This is the interface used by the ObjectStorageGateway to invoke operations
 * on the provider backend.
 * @author zhill
 *
 */
public abstract class ObjectStorageProviderClient {
	
	/*
	 * Service lifecycle operations
	 */
	public abstract void checkPreconditions() throws EucalyptusCloudException;
	public abstract void initialize() throws EucalyptusCloudException;
	public abstract void check() throws EucalyptusCloudException;
	public abstract void start() throws EucalyptusCloudException;
	public abstract void stop() throws EucalyptusCloudException;
	public abstract void enable() throws EucalyptusCloudException;
	public abstract void disable() throws EucalyptusCloudException;

	/* 
	 * -------------------------
	 * Service Operations
	 * -------------------------
	 */

	/**
	 * List all buckets accessible by the user.
	 * @param request
	 * @return
	 * @throws EucalyptusCloudException
	 */
	public abstract ListAllMyBucketsResponseType listAllMyBuckets(
			ListAllMyBucketsType request) throws EucalyptusCloudException;

	/*
	 * -------------------------
	 * Bucket Operations
	 * -------------------------
	 */
	/**
	 * Handles a HEAD request to the bucket. Just returns 200ok if bucket exists and user has access. Otherwise
	 * returns 404 if not found or 403 if no accesss.
	 * @param request
	 * @return
	 * @throws EucalyptusCloudException
	 */
	public abstract HeadBucketResponseType headBucket(HeadBucketType request)
			throws EucalyptusCloudException;

	public abstract CreateBucketResponseType createBucket(
			CreateBucketType request) throws EucalyptusCloudException;

	public abstract DeleteBucketResponseType deleteBucket(
			DeleteBucketType request) throws EucalyptusCloudException;

	public abstract GetBucketAccessControlPolicyResponseType getBucketAccessControlPolicy(
			GetBucketAccessControlPolicyType request)
			throws EucalyptusCloudException;

	public abstract ListBucketResponseType listBucket(ListBucketType request)
			throws EucalyptusCloudException;

	public abstract SetBucketAccessControlPolicyResponseType setBucketAccessControlPolicy(
			SetBucketAccessControlPolicyType request)
			throws EucalyptusCloudException;

	public abstract SetRESTBucketAccessControlPolicyResponseType setRESTBucketAccessControlPolicy(
			SetRESTBucketAccessControlPolicyType request)
			throws EucalyptusCloudException;
	
	public abstract GetBucketLocationResponseType getBucketLocation(
			GetBucketLocationType request) throws EucalyptusCloudException;

	public abstract SetBucketLoggingStatusResponseType setBucketLoggingStatus(
			SetBucketLoggingStatusType request) throws EucalyptusCloudException;

	public abstract GetBucketLoggingStatusResponseType getBucketLoggingStatus(
			GetBucketLoggingStatusType request) throws EucalyptusCloudException;

	public abstract GetBucketVersioningStatusResponseType getBucketVersioningStatus(
			GetBucketVersioningStatusType request)
			throws EucalyptusCloudException;

	public abstract SetBucketVersioningStatusResponseType setBucketVersioningStatus(
			SetBucketVersioningStatusType request)
			throws EucalyptusCloudException;

	public abstract ListVersionsResponseType listVersions(
			ListVersionsType request) throws EucalyptusCloudException;

	/*
	 * -------------------------
	 * Object Operations
	 * ------------------------- 
	 */	
	
	public abstract PutObjectResponseType putObject(PutObjectType request, InputStream inputData)
			throws EucalyptusCloudException;

	public abstract PostObjectResponseType postObject(PostObjectType request)
			throws EucalyptusCloudException;

	public abstract PutObjectInlineResponseType putObjectInline(
			PutObjectInlineType request) throws EucalyptusCloudException;

	@Deprecated
	public abstract AddObjectResponseType addObject(AddObjectType request)
			throws EucalyptusCloudException;

	public abstract DeleteObjectResponseType deleteObject(
			DeleteObjectType request) throws EucalyptusCloudException;


	public abstract GetObjectAccessControlPolicyResponseType getObjectAccessControlPolicy(
			GetObjectAccessControlPolicyType request)
			throws EucalyptusCloudException;
	
	public abstract SetObjectAccessControlPolicyResponseType setObjectAccessControlPolicy(
			SetObjectAccessControlPolicyType request)
			throws EucalyptusCloudException;

	public abstract SetRESTObjectAccessControlPolicyResponseType setRESTObjectAccessControlPolicy(
			SetRESTObjectAccessControlPolicyType request)
			throws EucalyptusCloudException;

	public abstract GetObjectResponseType getObject(GetObjectType request)
			throws EucalyptusCloudException;

	public abstract GetObjectExtendedResponseType getObjectExtended(
			GetObjectExtendedType request) throws EucalyptusCloudException;

		public abstract CopyObjectResponseType copyObject(CopyObjectType request)
			throws EucalyptusCloudException;

		public abstract DeleteVersionResponseType deleteVersion(
			DeleteVersionType request) throws EucalyptusCloudException;	
}