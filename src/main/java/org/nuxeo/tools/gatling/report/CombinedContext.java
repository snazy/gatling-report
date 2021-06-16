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
 *     Robert Stupp
 */
package org.nuxeo.tools.gatling.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CombinedContext {

    private final List<CombinedPart> parts;

    private final List<String> scripts;

    public CombinedContext(List<SimulationContext> contexts, List<String> scripts) {
        this.parts = new ArrayList<>(contexts.size());

        this.scripts = scripts;

        Map<String, List<SimulationContext>> contextsByName = new HashMap<>();
        for (SimulationContext stat : contexts) {
            contextsByName.computeIfAbsent(stat.getSimulationName(), k -> new ArrayList<>()).add(stat);
        }

        int id=0;
        for (Entry<String, List<SimulationContext>> group : contextsByName.entrySet()) {
            List<SimulationContext> list = group.getValue();
            for (SimulationContext sim : list) {
                sim.setId(Integer.toString(id++));
            }

            switch (list.size()) {
                case 1:
                    SimulationContext st = list.get(0);
                    this.parts.add(new CombinedPart(st.id, st.simulationName, PartType.SIMULATION, st));
                    break;
                case 2:
                    DiffContext diff = new DiffContext(list);
                    diff.setId(Integer.toString(id++));
                    this.parts.add(new CombinedPart(diff.getId(), diff.ref.simulationName, PartType.DIFF, diff));
                    break;
                default:
                    TrendContext trend = new TrendContext(list);
                    trend.setId(Integer.toString(id++));
                    this.parts.add(new CombinedPart(trend.getId(), trend.getRef().name, PartType.TREND, trend));
                    break;
            }
        }
    }

    public List<String> getScripts() {
        return scripts;
    }

    public List<CombinedPart> getParts() {
        return parts;
    }

    public static class CombinedPart {
        private final String id;
        private final String name;
        private final Object context;
        private final PartType type;

        public CombinedPart(String id, String name, PartType type, Object context) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.context = context;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Object getContext() {
            return context;
        }

        public PartType getType() {
            return type;
        }
    }

    public enum PartType {
        SIMULATION,
        DIFF,
        TREND
    }
}
