/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
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
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.objectstorage.providers.walrus;

import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.principal.AccessKey;
import com.eucalyptus.auth.principal.User;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.context.Context;
import com.eucalyptus.context.Contexts;
import com.eucalyptus.objectstorage.ObjectStorageProviders.ObjectStorageProviderClientProperty;
import com.eucalyptus.context.ServiceDispatchException;
import com.eucalyptus.objectstorage.exceptions.ObjectStorageException;
import com.eucalyptus.objectstorage.exceptions.s3.NotImplementedException;
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
import com.eucalyptus.objectstorage.msgs.HeadBucketResponseType;
import com.eucalyptus.objectstorage.msgs.HeadBucketType;
import com.eucalyptus.objectstorage.msgs.ListAllMyBucketsResponseType;
import com.eucalyptus.objectstorage.msgs.ListAllMyBucketsType;
import com.eucalyptus.objectstorage.msgs.ListBucketResponseType;
import com.eucalyptus.objectstorage.msgs.ListBucketType;
import com.eucalyptus.objectstorage.msgs.ListVersionsResponseType;
import com.eucalyptus.objectstorage.msgs.ListVersionsType;
import com.eucalyptus.objectstorage.msgs.ObjectStorageRequestType;
import com.eucalyptus.objectstorage.msgs.ObjectStorageResponseType;
import com.eucalyptus.objectstorage.msgs.PostObjectResponseType;
import com.eucalyptus.objectstorage.msgs.PostObjectType;
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
import com.eucalyptus.objectstorage.providers.s3.S3ProviderClient;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.SynchronousClient;
import com.eucalyptus.util.SynchronousClient.SynchronousClientException;
import com.eucalyptus.walrus.Walrus;
import com.eucalyptus.walrus.exceptions.WalrusException;
import com.eucalyptus.walrus.msgs.WalrusRequestType;
import com.eucalyptus.walrus.msgs.WalrusResponseType;

/**
 * The provider client that is used by the OSG to communicate with the Walrus backend.
 * Because Walrus is IAM-aware, this provider does *not* perform IAM policy checks itself.
 * 
 * WalrusProviderClient leverages the AWS Java SDK for the GET/PUT data operations on Walrus.
 * All metadata operations are handled using normal Eucalyptus message delivery
 *
 */
@ObjectStorageProviderClientProperty("walrus")
public class WalrusProviderClient extends S3ProviderClient {
	private static Logger LOG = Logger.getLogger(WalrusProviderClient.class);
	private static final int CONNECTION_TIMEOUT_MS = 500;
	private AmazonS3Client walrusDataClient;

	/**
	 * Class for handling the message pass-thru
	 *
	 */
	private static class WalrusClient extends SynchronousClient<WalrusRequestType, Walrus> {

		WalrusClient( final String userId ) {
			super( userId, Walrus.class );
		}
	}

	@Override
	public void initialize() throws EucalyptusCloudException {
		check();
		this.setUsePathStyle(true);
	}

	/**
	 * Simply looks up the currently enabled Walrus service.
	 * @return
	 */
	private static WalrusClient getEnabledWalrusClient(String userId) throws ObjectStorageException {
		WalrusClient c = new WalrusClient(Contexts.lookup().getUser().getUserId());
		try {
			c.init();
			return c;
		} catch (SynchronousClientException e) {
			LOG.error("Error initializing client for walrus pass-thru",e);
			throw new ObjectStorageException("Could not initialize walrus client");
		}		
	}

	/**
	 * Returns a usable S3 Client configured to send requests to the currently configured
	 * endpoint with the currently configured credentials.
	 * @return
	 */
	@Override
	protected AmazonS3Client getS3Client(User requestUser, String requestAWSAccessKeyId) {
		//TODO: this should be enhanced to share clients/use a pool for efficiency.
		if (walrusDataClient == null) {
			synchronized(this) {
				if(walrusDataClient == null) {
					ClientConfiguration config = new ClientConfiguration();
					config.setProtocol(Protocol.HTTP);
					config.setConnectionTimeout(CONNECTION_TIMEOUT_MS); //very short timeout
					AWSCredentials credentials = mapCredentials(requestUser, requestAWSAccessKeyId);
					AmazonS3Client client = new AmazonS3Client(credentials, config);
					ServiceConfiguration walrus = Topology.lookup(Walrus.class);
					client.setEndpoint(walrus.getUri().toString());		
					client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(this.usePathStyle));
					walrusDataClient = client;
				}
			}
		}
		return walrusDataClient;
	}

	/**
	 * Walrus provider mapping is the identity function (users map to themselves)
	 */
	@Override
	protected  BasicAWSCredentials mapCredentials(User requestUser, String requestAWSAccessKeyId) throws NoSuchElementException, IllegalArgumentException {
		try {
			AccessKey userKey = requestUser.getKey(requestAWSAccessKeyId);
			return new BasicAWSCredentials(userKey.getAccessKey(), userKey.getSecretKey());	
		} catch(AuthException e){
			throw new NoSuchElementException("No valid key found");
		}

	}

	/**
	 * Do the request proxying
	 * @param context
	 * @param request
	 * @param walrusRequestClass
	 * @param walrusResponseClass
	 * @return
	 */
	private <ObjResp extends ObjectStorageResponseType, 
	ObjReq extends ObjectStorageRequestType, 
	WalResp extends WalrusResponseType, 
	WalReq extends WalrusRequestType> ObjResp proxyRequest(Context context, ObjReq request, Class<WalReq> walrusRequestClass, Class<WalResp> walrusResponseClass) throws EucalyptusCloudException {
		ObjectStorageException osge = null;
		try  {
			WalrusClient c = getEnabledWalrusClient(context.getUser().getUserId());
			WalReq walrusRequest = MessageMapper.INSTANCE.proxyWalrusRequest(walrusRequestClass, request);
			WalResp walrusResponse = c.sendSync(walrusRequest);
			ObjResp reply = MessageMapper.INSTANCE.proxyWalrusResponse(request, walrusResponse);
			return reply;
		} catch(ServiceDispatchException e) {
			Throwable rootCause = e.getRootCause();
			if(rootCause != null) {
				if (rootCause instanceof WalrusException) {
					osge = MessageMapper.INSTANCE.proxyWalrusException((WalrusException)rootCause);
				} else {
					throw new EucalyptusCloudException(rootCause);
				}
			}
		} catch(Exception e) {
			throw new EucalyptusCloudException(e);
		}
		if(osge != null) {
			throw osge;
		}
		throw new EucalyptusCloudException("Unable to obtain reply from dispatcher.");
	}

	/*
	 * -------------------------
	 * Service Operations
	 * ------------------------- 
	 */	


	@Override
	public ListAllMyBucketsResponseType listAllMyBuckets(ListAllMyBucketsType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.ListAllMyBucketsType.class, com.eucalyptus.walrus.msgs.ListAllMyBucketsResponseType.class);
		} catch(EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	/*
	 * -------------------------
	 * Bucket Operations
	 * ------------------------- 
	 */		
	@Override
	public CreateBucketResponseType createBucket(CreateBucketType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.CreateBucketType.class, com.eucalyptus.walrus.msgs.CreateBucketResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}	
	}

	@Override
	public DeleteBucketResponseType deleteBucket(DeleteBucketType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.DeleteBucketType.class, com.eucalyptus.walrus.msgs.DeleteBucketResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}		
	}

	@Override
	public HeadBucketResponseType headBucket(HeadBucketType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.HeadBucketType.class, com.eucalyptus.walrus.msgs.HeadBucketResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}	
	}

	@Override
	public GetBucketAccessControlPolicyResponseType getBucketAccessControlPolicy(GetBucketAccessControlPolicyType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.GetBucketAccessControlPolicyType.class, com.eucalyptus.walrus.msgs.GetBucketAccessControlPolicyResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public ListBucketResponseType listBucket(ListBucketType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.ListBucketType.class, com.eucalyptus.walrus.msgs.ListBucketResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public SetBucketAccessControlPolicyResponseType setBucketAccessControlPolicy(SetBucketAccessControlPolicyType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.SetBucketAccessControlPolicyType.class, com.eucalyptus.walrus.msgs.SetBucketAccessControlPolicyResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public SetRESTBucketAccessControlPolicyResponseType setRESTBucketAccessControlPolicy(SetRESTBucketAccessControlPolicyType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.SetRESTBucketAccessControlPolicyType.class, com.eucalyptus.walrus.msgs.SetRESTBucketAccessControlPolicyResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public GetBucketLocationResponseType getBucketLocation(GetBucketLocationType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.GetBucketLocationType.class, com.eucalyptus.walrus.msgs.GetBucketLocationResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public SetBucketLoggingStatusResponseType setBucketLoggingStatus(SetBucketLoggingStatusType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.SetBucketLoggingStatusType.class, com.eucalyptus.walrus.msgs.SetBucketLoggingStatusResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public GetBucketLoggingStatusResponseType getBucketLoggingStatus(GetBucketLoggingStatusType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.GetBucketLoggingStatusType.class, com.eucalyptus.walrus.msgs.GetBucketLoggingStatusResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public GetBucketVersioningStatusResponseType getBucketVersioningStatus(GetBucketVersioningStatusType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.GetBucketVersioningStatusType.class, com.eucalyptus.walrus.msgs.GetBucketVersioningStatusResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public SetBucketVersioningStatusResponseType setBucketVersioningStatus(SetBucketVersioningStatusType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.SetBucketVersioningStatusType.class, com.eucalyptus.walrus.msgs.SetBucketVersioningStatusResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public ListVersionsResponseType listVersions(ListVersionsType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.ListVersionsType.class, com.eucalyptus.walrus.msgs.ListVersionsResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public DeleteVersionResponseType deleteVersion(DeleteVersionType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.DeleteVersionType.class, com.eucalyptus.walrus.msgs.DeleteVersionResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	/*
	 * -------------------------
	 * Object Operations
	 * ------------------------- 
	 */
	
	//GET and PUT object are the same as the base S3ProviderClient.

	@Override
	public PostObjectResponseType postObject(PostObjectType request) throws EucalyptusCloudException {
		/*try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.PostObjectType.class, com.eucalyptus.walrus.msgs.PostObjectResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}*/
		throw new NotImplementedException("PostObject");
	}

	@Override
	public CopyObjectResponseType copyObject(CopyObjectType request) throws EucalyptusCloudException {		
		/*try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.CopyObjectType.class, com.eucalyptus.walrus.msgs.CopyObjectResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}*/
		throw new NotImplementedException("CopyObject");
	}

	@Override
	public DeleteObjectResponseType deleteObject(DeleteObjectType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.DeleteObjectType.class, com.eucalyptus.walrus.msgs.DeleteObjectResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public GetObjectAccessControlPolicyResponseType getObjectAccessControlPolicy(GetObjectAccessControlPolicyType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.GetObjectAccessControlPolicyType.class, com.eucalyptus.walrus.msgs.GetObjectAccessControlPolicyResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public SetObjectAccessControlPolicyResponseType setObjectAccessControlPolicy(SetObjectAccessControlPolicyType request) throws EucalyptusCloudException {	
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.SetObjectAccessControlPolicyType.class, com.eucalyptus.walrus.msgs.SetObjectAccessControlPolicyResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}

	@Override
	public SetRESTObjectAccessControlPolicyResponseType setRESTObjectAccessControlPolicy(SetRESTObjectAccessControlPolicyType request) throws EucalyptusCloudException {
		try {
			return proxyRequest(Contexts.lookup(), request, com.eucalyptus.walrus.msgs.SetRESTObjectAccessControlPolicyType.class, com.eucalyptus.walrus.msgs.SetRESTObjectAccessControlPolicyResponseType.class);			
		} catch (EucalyptusCloudException e) {
			LOG.error("Error response from Walrus", e);
			throw e;
		}
	}


}
