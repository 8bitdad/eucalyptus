/*******************************************************************************
 * Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */

package com.eucalyptus.vm;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.persistence.EntityTransaction;
import org.apache.log4j.Logger;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.network.NetworkGroup;
import com.eucalyptus.network.NetworkPeer;
import com.eucalyptus.network.NetworkRule;
import com.eucalyptus.util.ByteArray;
import com.eucalyptus.vm.VmInstance.VmState;
import com.eucalyptus.vm.VmInstance.VmStateSet;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class NetworkGroupsMetadata implements Function<MetadataRequest, ByteArray> {
  private static Logger                  LOG        = Logger.getLogger( NetworkGroupsMetadata.class );
  private static Lock                    lock       = new ReentrantLock( );
  private static Long                    lastTime   = 0l;
  private static AtomicReference<String> topoString = new AtomicReference<String>( "" );
  
  private String getNetworkTopology( ) {
    return generateTopology( );//GRZE:FIXME: this is stupid and will perform poorly.
  }
  
  private boolean checkInterval( ) {
    return ( lastTime + refreshInterval( ) ) < System.currentTimeMillis( );
  }
  
  private long refreshInterval( ) {
    return VmInstances.NETWORK_METADATA_REFRESH_TIME * 1000l;
  }
  
  public String generateTopology( ) {
    StringBuilder buf = new StringBuilder( );
    Multimap<String, String> networks = ArrayListMultimap.create( );
    Multimap<String, String> rules = ArrayListMultimap.create( );
    EntityTransaction db = Entities.get( VmInstance.class );
    try {
      Predicate<VmInstance> filter = Predicates.and( VmState.TERMINATED.not( ), VmState.STOPPED.not( ) );
      for ( VmInstance vm : VmInstances.list( filter ) ) {
        try {
          for ( NetworkGroup ruleGroup : vm.getNetworkGroups( ) ) {
            try {
              ruleGroup = Entities.merge( ruleGroup );
              networks.put( ruleGroup.getClusterNetworkName( ), vm.getPrivateAddress( ) );
              if ( !rules.containsKey( ruleGroup.getNaturalId( ) ) ) {
                for ( NetworkRule netRule : ruleGroup.getNetworkRules( ) ) {
                  try {
                    String rule = String.format( "-P %s -%s %d%s%d ", netRule.getProtocol( ), ( NetworkRule.Protocol.icmp.equals( netRule.getProtocol( ) )
                      ? "t"
                      : "p" ), netRule.getLowPort( ), ( NetworkRule.Protocol.icmp.equals( netRule.getProtocol( ) )
                      ? ":"
                      : "-" ), netRule.getHighPort( ) );
                    for ( NetworkPeer peer : netRule.getNetworkPeers( ) ) {
                      String ruleString = String.format( "%s -o %s -u %s", rule, peer.getGroupName( ), peer.getUserQueryKey( ) );
                      if ( !rules.get( ruleGroup.getClusterNetworkName( ) ).contains( ruleString ) ) {
                        rules.put( ruleGroup.getClusterNetworkName( ), ruleString );
                      }
                    }
                    for ( String cidr : netRule.getIpRanges( ) ) {
                      String ruleString = String.format( "%s -s %s", rule, cidr );
                      if ( !rules.get( ruleGroup.getClusterNetworkName( ) ).contains( ruleString ) ) {
                        rules.put( ruleGroup.getClusterNetworkName( ), ruleString );
                      }
                    }
                  } catch ( Exception ex ) {
                    LOG.error( ex, ex );
                  }
                }
              }
            } catch ( Exception ex ) {
              LOG.error( ex, ex );
            }
          }
        } catch ( Exception ex ) {
          LOG.error( ex , ex );
        }
      }
      buf.append( rulesToString( rules ) );
      buf.append( groupsToString( networks ) );
      db.rollback( );
    } catch ( Exception ex ) {
      LOG.error( ex, ex );
      db.rollback( );
    }
    return buf.toString( );
  }
  
  private static String groupsToString( Multimap<String, String> networks ) {
    StringBuilder buf = new StringBuilder( );
    for ( String networkName : networks.keySet( ) ) {
      buf.append( "GROUP " ).append( networkName );
      for ( String ip : networks.get( networkName ) ) {
        buf.append( " " ).append( ip );
      }
      buf.append( "\n" );
    }
    return buf.toString( );
  }
  
  private static String rulesToString( Multimap<String, String> rules ) {
    StringBuilder buf = new StringBuilder( );
    for ( String networkName : rules.keySet( ) ) {
      for ( String rule : rules.get( networkName ) ) {
        buf.append( "RULE " ).append( networkName ).append( " " ).append( rule ).append( "\n" );
      }
    }
    return buf.toString( );
  }
  
  @Override
  public ByteArray apply( MetadataRequest arg0 ) {
    return ByteArray.newInstance( getNetworkTopology( ) );
  }
}