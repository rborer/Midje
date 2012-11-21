(ns midje.t-repl
  (:use [midje.sweet]
        [midje.repl]
        [clojure.pprint]
        [midje.test-util])
  (:require [midje.internal-ideas.compendium :as compendium]
            midje.util))

                                ;;; Forgetting facts
(defn add-fact
  [& args]
  (compendium/record-fact-existence!
   (with-meta (gensym)
     (merge {:midje/namespace 'midje.sweet :midje/name "with a name"
             :a-property true :midje/source '(code)}
            (apply hash-map args)))))


;; all of them
(fact (count (fetch-facts :all)) => pos?)
(forget-facts :all)
(fact :check-only-at-load-time
  (fetch-facts :all) => empty?)

;; no argument defaults to this namespace
(add-fact :midje/namespace (ns-name *ns*))
(forget-facts)
(fact :check-only-at-load-time
  (fetch-facts :all) => empty)

(add-fact :midje/namespace 'midje.sweet)
(forget-facts)
(fact :check-only-at-load-time "won't change anything"
  (count (fetch-facts :all)) => 1)
(forget-facts :all)

;; Explicit argument
(add-fact :midje/namespace (ns-name *ns*))
(add-fact :midje/namespace 'midje.sweet)
(forget-facts *ns*)
(fact :check-only-at-load-time
  (count (fetch-facts *ns*)) => zero?
  (count (fetch-facts 'midje.sweet)) => pos?)

(forget-facts 'midje.sweet)
(fact :check-only-at-load-time
  (count (fetch-facts *ns*)) => zero?
  (count (fetch-facts 'midje.sweet)) => zero?)

;; By name

(defn names
  ([]
     (names (fetch-facts :all)))
  ([facts]
     (map fact-name facts)))

(defn three-names []
  (add-fact :midje/name "abcde")
  (add-fact :midje/name "bcdef")
  (add-fact :midje/name "ghi"))

(three-names)
(forget-facts "bcd")
(fact :check-only-at-load-time
  (names) => ["ghi"])

(forget-facts)
(three-names)
(forget-facts #"bcd")
(fact :check-only-at-load-time
  (names) => ["ghi"])
(forget-facts :all)

;; By metadata
(add-fact :midje/name "fred")
(add-fact :midje/name "betty" :translation true)

(forget-facts :translation)
(fact :check-only-at-load-time
  (names) => ["fred"])
(forget-facts :all)

;; By predicate
(add-fact :midje/name "to be removed" :a true :b true)
(add-fact :midje/name "bad-b" :a true :b false)
(add-fact :midje/name "bad-a" :a false :b true)

(forget-facts #(and (:a %) (:b %)))
(fact :check-only-at-load-time
  (names) => (contains "bad-a" "bad-b" :in-any-order))
(forget-facts :all)







(comment



                                ;;; Rechecking last-checked fact


(def run-count (atom 0))

(fact
  (reset! run-count 1)
  (+ 1 1) => 2)

(recheck-fact)

(fact :check-only-at-load-time
  @run-count => 1)

(let [definition (source-of-last-fact-checked)]
  (fact definition => '(fact (reset! run-count 1) (+ 1 1) => 2)))

;; Nesting of facts and most-recently-run fact

(def outer-run-count (atom 0))
(def inner-run-count (atom 0))
(fact outer
  (swap! outer-run-count inc)
  (+ 1 1) => 2
  (fact inner
    (swap! inner-run-count inc)
    (fact (- 1 1) => 0)))

(recheck-fact)

(fact "The last fact check is the outermost nested check"
  :check-only-at-load-time
  (fact-name (last-fact-checked)) => "outer"
  @outer-run-count => 2
  @inner-run-count => 2)

;; Multiple nested facts

(def run-count (atom 0))
(fact "outermost"
  (fact "inner 1"
    (swap! run-count inc))
  (fact "inner 2"
    (swap! run-count inc)))

(recheck-fact)

(fact :check-only-at-load-time
  @run-count => 4)


;; Tabular facts count as nested facts

(def run-count (atom 0))

(tabular "tabular facts count as last-fact checked"
  (fact
    (swap! run-count inc)
    (+ ?a ?b) => ?c)
  ?a ?b ?c
  1  2  3
  2  2  4)

(recheck-fact)

(fact :check-only-at-load-time
  @run-count => 4)

;; Facts mark themselves as last-fact-checked each time they're
;; rechecked.  Note that a new fact-function is spawned off for
;; storage each time a fact is run. This is a side effect of the need
;; to keep metadata around. There may be a way to avoid the
;; duplication, but I don't see it right now.
(fact (+ 1 1) => 2)
(def one-plus-one (last-fact-checked))
(fact (+ 2 2) => 4)
(def two-plus-two (last-fact-checked))

(recheck-fact)

(fact :check-only-at-load-time
  (fact-source (last-fact-checked)) => (fact-source two-plus-two))

(one-plus-one)
(fact :check-only-at-load-time
  (fact-source (last-fact-checked)) => (exactly (fact-source one-plus-one)))





                                ;;; Which facts are stored in the compendium

;; Nested facts are not.

(forget-facts)

(def inner-count (atom 0))
(def outer-count (atom 0))

(fact "outer"
  1 => 1
  (swap! outer-count inc)
  (fact "inner"
    (swap! inner-count inc)
    2 => 2))

(fact "only outer fact is available"
  :check-only-at-load-time
  (count (all-facts)) => 1
  (fact-name (first (all-facts))) => "outer")

;; Both get run, though.
(unobtrusive-check-facts)

(fact
  @outer-count => 2
  @inner-count => 2)



                                ;;; Running facts from the compendium
(forget-facts)

;; Nothing to do
(unobtrusive-check-facts)
(unobtrusive-check-facts *ns*)

;; failures do not prevent later facts from being rechecked

(forget-facts)
(def succeed (atom false))
(def fail (atom false))

(run-silently
 (fact "1"
   (reset! fail :fail)
   (+ 1 2) => 3333))
(fact "2"
  (reset! succeed :succeed)
  (+ 1 2) => 3)

(reset! succeed false)
(reset! fail false)
(run-silently (unobtrusive-check-facts))

(fact "Both facts were checked"
  :check-only-at-load-time
  @succeed => :succeed
  @fail => :fail)



;;; Variant ways of using unobtrusive-check-facts with namespaces

(forget-facts)

(def named-fact-count (atom 0))
(def anonymous-fact-count (atom 0))

(fact "my fact"
  (swap! named-fact-count inc)
  (+ 1 1) => 2)

(fact 
  (swap! anonymous-fact-count inc)
  (+ 2 2) => 4)

(unobtrusive-check-facts)                           ; No namespace runs everything
(fact :check-only-at-load-time
  @named-fact-count => 2
  @anonymous-fact-count => 2)


(unobtrusive-check-facts *ns*)                      ; Explicit namespace arg
(fact :check-only-at-load-time
  @named-fact-count => 3
  @anonymous-fact-count => 3)

(unobtrusive-check-facts (ns-name *ns*))             ; Symbol namespace name
(fact :check-only-at-load-time
  @named-fact-count => 4
  @anonymous-fact-count => 4)

(unobtrusive-check-facts 'clojure.core)             ; A different namespace
(fact :check-only-at-load-time
  @named-fact-count => 4
  @anonymous-fact-count => 4)

(unobtrusive-check-facts *ns* *ns*)                 ; Multiple args
(fact :check-only-at-load-time
  @named-fact-count => 6
  @anonymous-fact-count => 6)




                                ;;; How one redefines facts
(forget-facts)

(def run-count (atom 0))

(fact "name"
  (swap! run-count inc))

(fact "name"
  @run-count => 1)

;; If two facts were now defined, the run-count would
;; increment when we do this:

(unobtrusive-check-facts)
(fact "But only one is defined"
  :check-only-at-load-time
  @run-count => 1)

;; Redefinition to an identical form does not produce copies
(reset! run-count 0)
(forget-facts)

(fact
  (swap! run-count inc)
  (+ 1 2) => 3)
(fact :check-only-at-load-time @run-count => 1)

(fact
  (swap! run-count inc)
  (+ 1 2) => 3)
(fact :check-only-at-load-time @run-count => 2)

(fact "There is still only one defined fact."
  :check-only-at-load-time
  (count (all-facts)) => 1)

(unobtrusive-check-facts)
(fact :check-only-at-load-time @run-count => 3)



                                ;;; Run facts matching a predicate
(forget-facts)

(def unobtrusive-fact-run-count (atom 0))
(def integration-run-count (atom 0))

(fact unobtrusive-fact
  (swap! unobtrusive-fact-run-count inc))
(fact :integration
  (swap! integration-run-count inc))


(check-matching-facts #(-> % :midje/name (= "unobtrusive-fact")))

(fact
  @unobtrusive-fact-run-count => 2
  @integration-run-count => 1)

(check-matching-facts :integration)
(fact
  @unobtrusive-fact-run-count => 2
  @integration-run-count => 2)



)


(midje.util/expose-testables midje.repl)
(alias 'ctf 'midje.clojure-test-facade)

(fact "can find paths to load from project.clj"
  (fact "if it exists"
    (paths-to-load) => ["/test1" "/src1"]
    (provided (leiningen.core.project/read) => {:test-paths ["/test1"]
                                                :source-paths ["/src1"]}))

  (fact "and provides a default if it does not"
    (paths-to-load) => ["test"]
    (provided (leiningen.core.project/read)
              =throws=> (new java.io.FileNotFoundException))))


(fact "expand-namespaces returns namespace symbols"
  (fact "from symbols or strings"
    (expand-namespaces ["explicit-namespace1"]) => ['explicit-namespace1]
    (expand-namespaces ['explicit-namespace2]) => ['explicit-namespace2])

  (fact "can 'unglob' wildcards"
    (expand-namespaces ["ns.foo.*"]) => '[ns.foo.bar ns.foo.baz]
    (provided (bultitude.core/namespaces-on-classpath :prefix "ns.foo.")
              => '[ns.foo.bar ns.foo.baz])

    (expand-namespaces ['ns.foo.*]) => '[ns.foo.bar ns.foo.baz]
    (provided (bultitude.core/namespaces-on-classpath :prefix "ns.foo.")
              => '[ns.foo.bar ns.foo.baz])))

(fact "load-facts"
  (against-background ; These always happen.
    (ctf/zero-counters) => anything
    ;; Lookup expanded namespaces
    (require ...expanded... :reload) => anything
    (forget-facts ..expanded..) => anything
    (#'midje.repl/report-summary) => anything)

  (load-facts 'ns.foo) => nil
  (provided
    (#'midje.repl/expand-namespaces ['ns.foo]) => [..expanded..])

  (load-facts :verbose 'ns.foo) => nil
  (provided
    (#'midje.repl/expand-namespaces ['ns.foo]) => [..expanded..]
    (println anything) => nil)

  (load-facts) => nil
  (provided
    (#'midje.repl/paths-to-load) => [..path..]
    (bultitude.core/namespaces-in-dir ..path..) => [...expanded...]))

