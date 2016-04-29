/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.core

import org.gradle.util.Matchers
import spock.lang.Specification

class ModelPathTest extends Specification {
    def "root path has no parent"() {
        def path = ModelPath.ROOT

        expect:
        path.toString() == "<root>"
        path.components == []
        path.name == ""
        path.depth == 0
        path.parent == null
        path.rootParent == null
    }

    def "path with single component is top level"() {
        def path = ModelPath.path("p")

        expect:
        path.toString() == "p"
        path.components == ["p"]
        path.name == "p"
        path.depth == 1
        path.parent == ModelPath.ROOT
        path.rootParent == null
        path == ModelPath.ROOT.child("p")
    }

    def "path with multiple components"() {
        def path = ModelPath.path(["a", "b", "c"])

        expect:
        path.toString() == "a.b.c"
        path.components == ["a", "b", "c"]
        path.name == "c"
        path.depth == 3
        path.parent == ModelPath.path(["a", "b"])
        path.parent.toString() == "a.b"
        path.rootParent == ModelPath.path("a")
        path == ModelPath.ROOT.child("a").child("b").child("c")
    }

    def "equals and hashcode"() {
        def p1 = ModelPath.path("a")

        expect:
        Matchers.strictlyEquals(p1, ModelPath.path("a"))
        p1 != ModelPath.path(("b"))
        p1 != ModelPath.path(("abc"))
        p1 != ModelPath.path(("a.b"))
        p1 != p1.parent
        p1 != p1.child("b")
    }

    def "is direct child"() {
        expect:
        ModelPath.ROOT.isDirectChild(ModelPath.path("p"))
        !ModelPath.ROOT.isDirectChild(ModelPath.ROOT)
        !ModelPath.ROOT.isDirectChild(ModelPath.path("a.b"))

        ModelPath.path("a.b").isDirectChild(ModelPath.path("a.b.c"))
        !ModelPath.path("a.b").isDirectChild(ModelPath.path("a.b.c.d"))
        !ModelPath.path("a.b").isDirectChild(ModelPath.path("a.a.b"))
        !ModelPath.path("a.b").isDirectChild(ModelPath.path("a.b"))
        !ModelPath.path("a.b").isDirectChild(ModelPath.path("a"))
        !ModelPath.path("a.b").isDirectChild(ModelPath.path("c.a.b.c"))
        !ModelPath.path("a.b").isDirectChild(null)
    }

    def "is descendant"() {
        expect:
        ModelPath.ROOT.isDescendant(ModelPath.path("p"))
        ModelPath.ROOT.isDescendant(ModelPath.path("a.b"))
        !ModelPath.ROOT.isDescendant(ModelPath.ROOT)

        ModelPath.path("a.b").isDescendant(ModelPath.path("a.b.c"))
        ModelPath.path("a.b").isDescendant(ModelPath.path("a.b.c.d.e"))
        !ModelPath.path("a.b").isDescendant(ModelPath.path("a.a"))
        !ModelPath.path("a.b").isDescendant(ModelPath.path("a.a.b"))
        !ModelPath.path("a.b").isDescendant(ModelPath.path("a.b"))
        !ModelPath.path("a.b").isDescendant(ModelPath.path("a"))
        !ModelPath.path("a.b").isDescendant(ModelPath.path("c.a.b.c"))
        !ModelPath.path("a.b").isDescendant(null)
    }

    def "can create paths for indirect descendants"() {
        expect:
        ModelPath.ROOT.descendant(ModelPath.path("c.d")) == ModelPath.path("c.d")
        ModelPath.path("a.b").descendant(ModelPath.path("c.d")) == ModelPath.path("a.b.c.d")
    }
}
