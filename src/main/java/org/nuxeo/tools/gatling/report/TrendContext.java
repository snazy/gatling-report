/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Benoit Delbosc
 */
package org.nuxeo.tools.gatling.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TrendContext {

    protected final String scenario;

    protected final TrendStat all = new TrendStat();

    protected final List<TrendStat> requests = new ArrayList<>();

    protected String id;

    protected List<String> scripts;

    public TrendContext(List<SimulationContext> stats) {
        Set<String> names = new HashSet<>();
        List<String> requestNames = getRequestListSorted(stats.get(0));
        Collections.reverse(requestNames);
        for (String requestName : requestNames) {
            requests.add(new TrendStat());
        }
        ArrayList<SimulationContext> orderedStats = new ArrayList<>(stats);
        orderedStats.sort((a, b) -> (int) (a.simStat.start - b.simStat.start));
        for (SimulationContext simStat : orderedStats) {
            names.add(simStat.simulationName);
            all.add(simStat.simStat);
            for (int i = 0; i < requestNames.size(); i++) {
                String name = requestNames.get(i);
                RequestStat reqStat = simStat.reqStats.get(name);
                requests.get(i).add(reqStat);
            }
        }
        scenario = String.join(" ", names);
    }

    public TrendContext setScripts(List<String> scripts) {
        this.scripts = scripts;
        return this;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    protected List<String> getRequestListSorted(SimulationContext stat) {
        return stat.getRequests().stream().map(s -> s.request).collect(Collectors.toList());
    }

    public TrendStat getRef() {
        return requests.get(0);
    }

    public TrendStat getChallenger() {
        return requests.get(1);
    }

    protected class TrendStat {
        protected final List<String> xvalues = new ArrayList<>();
        protected final List<Double> yvalues = new ArrayList<>();
        protected final List<Long> yerrors = new ArrayList<>();
        protected final List<Double> rps = new ArrayList<>();
        protected String name;
        protected Integer indice;

        public void add(RequestStat stat) {
            if (stat == null) {
                xvalues.add(null);
                yvalues.add(null);
                yerrors.add(null);
                rps.add(null);
            } else {
                name = stat.request;
                indice = stat.indice;
                xvalues.add(String.format("'%s'", stat.startDate));
                yvalues.add(stat.avg);
                yerrors.add(stat.stddev);
                rps.add(stat.rps);
            }
        }
    }

}
