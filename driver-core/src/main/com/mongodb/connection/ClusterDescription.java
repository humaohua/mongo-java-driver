/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.connection;

import com.mongodb.ServerAddress;
import com.mongodb.TagSet;
import com.mongodb.annotations.Immutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.mongodb.assertions.Assertions.notNull;
import static java.lang.String.format;

/**
 * Immutable snapshot state of a cluster.
 *
 * @since 3.0
 */
@Immutable
public class ClusterDescription {
    private final ClusterConnectionMode connectionMode;
    private final ClusterType type;
    private final Set<ServerDescription> all;

    public ClusterDescription(final ClusterConnectionMode connectionMode, final ClusterType type,
                              final List<ServerDescription> serverDescriptions) {
        notNull("all", serverDescriptions);
        this.connectionMode = notNull("connectionMode", connectionMode);
        this.type = notNull("type",
                            type);
        Set<ServerDescription> serverDescriptionSet = new TreeSet<ServerDescription>(new Comparator<ServerDescription>() {
            @Override
            public int compare(final ServerDescription o1, final ServerDescription o2) {
                return o1.getAddress().toString().compareTo(o2.getAddress().toString());
            }
        });
        serverDescriptionSet.addAll(serverDescriptions);
        this.all = Collections.unmodifiableSet(serverDescriptionSet);
    }

    /**
     * Return whether the cluster is compatible with the driver.
     *
     * @return true if the cluster is compatible with the driver.
     */
    public boolean isCompatibleWithDriver() {
        for (final ServerDescription cur : all) {
            if (!cur.isCompatibleWithDriver()) {
                return false;
            }
        }
        return true;
    }

    public ClusterConnectionMode getConnectionMode() {
        return connectionMode;
    }

    public ClusterType getType() {
        return type;
    }

    /**
     * Returns the Set of all server descriptions in this cluster, sorted by the String value of the ServerAddress of each one.
     *
     * @return the set of server descriptions
     */
    public Set<ServerDescription> getAll() {
        return all;
    }


    public ServerDescription getByServerAddress(final ServerAddress serverAddress) {
        for (final ServerDescription cur : getAll()) {
            if (cur.getAddress().equals(serverAddress)) {
                return cur;
            }
        }
        return null;
    }

    /**
     * While it may seem counter-intuitive that a MongoDb cluster can have more than one primary, it can in the case where the client's view
     * of the cluster is a set of mongos servers, any of which can serve as the primary.
     *
     * @return a list of servers that can act as primaries\
     */
    public List<ServerDescription> getPrimaries() {
        return getServersByPredicate(new Predicate() {
            public boolean apply(final ServerDescription serverDescription) {
                return serverDescription.isPrimary();
            }
        });
    }

    public List<ServerDescription> getSecondaries() {
        return getServersByPredicate(new Predicate() {
            public boolean apply(final ServerDescription serverDescription) {
                return serverDescription.isSecondary();
            }
        });
    }

    public List<ServerDescription> getSecondaries(final TagSet tagSet) {
        return getServersByPredicate(new Predicate() {
            public boolean apply(final ServerDescription serverDescription) {
                return serverDescription.isSecondary() && serverDescription.hasTags(tagSet);
            }
        });
    }

    public List<ServerDescription> getAny() {
        return getServersByPredicate(new Predicate() {
            public boolean apply(final ServerDescription serverDescription) {
                return serverDescription.isOk();
            }
        });
    }

    public List<ServerDescription> getAnyPrimaryOrSecondary() {
        return getServersByPredicate(new Predicate() {
            public boolean apply(final ServerDescription serverDescription) {
                return serverDescription.isPrimary() || serverDescription.isSecondary();
            }
        });
    }

    public List<ServerDescription> getAnyPrimaryOrSecondary(final TagSet tagSet) {
        return getServersByPredicate(new Predicate() {
            public boolean apply(final ServerDescription serverDescription) {
                return (serverDescription.isPrimary() || serverDescription.isSecondary()) && serverDescription.hasTags(tagSet);
            }
        });
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClusterDescription that = (ClusterDescription) o;

        if (!all.equals(that.all)) {
            return false;
        }
        if (connectionMode != that.connectionMode) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = all.hashCode();
        result = 31 * result + connectionMode.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ClusterDescription{"
               + "type=" + getType()
               + ", connectionMode=" + connectionMode
               + ", all=" + all
               + '}';
    }

    public String getShortDescription() {
        StringBuilder serverDescriptions = new StringBuilder();
        String delimiter = "";
        for (final ServerDescription cur : all) {
            serverDescriptions.append(delimiter).append(cur.getShortDescription());
            delimiter = ", ";
        }
        return format("{type=%s, servers=[%s]", type, serverDescriptions);
    }

    private interface Predicate {
        boolean apply(ServerDescription serverDescription);
    }

    private List<ServerDescription> getServersByPredicate(final Predicate predicate) {
        List<ServerDescription> membersByTag = new ArrayList<ServerDescription>();

        for (final ServerDescription cur : all) {
            if (predicate.apply(cur)) {
                membersByTag.add(cur);
            }
        }

        return membersByTag;
    }
}