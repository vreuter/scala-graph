package scalax.collection
package generic

sealed protected[collection] trait Eq { this: Edge[_] =>
  protected def baseEquals(other: Edge[_]): Boolean
  protected def baseHashCode: Int

  override def equals(other: Any): Boolean = other match {
    case that: Edge[_] =>
      (this eq that) ||
      (that canEqual this) &&
      this.isDirected == that.isDirected &&
      this.isInstanceOf[MultiEdge] == that.isInstanceOf[MultiEdge] &&
      equals(that)
    case _ => false
  }

  /** Preconditions:
    *  `this.directed == that.directed &&`
    *  `this.isInstanceOf[Keyed] == that.isInstanceOf[Keyed]`
    */
  protected def equals(other: Edge[_]): Boolean = baseEquals(other)

  override def hashCode: Int = baseHashCode

}

protected[collection] object Eq {
  /* Works for both sets and bags.
   */
  private def nrEqualingNodes(itA: OneOrMore[_], itB: OneOrMore[_], bLen: Int): Int = {
    var nr   = 0
    val used = new Array[Boolean](bLen)
    for (a <- itA) {
      val bs = itB.iterator
      var j  = 0
      while (j < bLen) {
        val b = bs.next()
        if (!used(j) && a == b) {
          nr += 1
          used(j) = true
          j = bLen
        }
        j += 1
      }
    }
    nr
  }

  def equalEnds(
      left: Edge[_],
      leftEnds: OneOrMore[_],
      right: Edge[_],
      rightEnds: OneOrMore[_]
  ): Boolean = {
    val thisOrdered = left.isInstanceOf[OrderedEndpoints]
    val thatOrdered = right.isInstanceOf[OrderedEndpoints]

    thisOrdered == thatOrdered && (
      if (thisOrdered) leftEnds == rightEnds
      else {
        val rightSize = rightEnds.size
        Eq.nrEqualingNodes(leftEnds, rightEnds, rightSize) == rightSize
      }
    )
  }
}

protected[collection] trait EqHyper extends Eq {
  this: AnyHyperEdge[_] =>

  override protected def baseEquals(other: Edge[_]): Boolean = {
    val (thisArity, thatArity) = (arity, other.arity)
    if (thisArity == thatArity)
      Eq.equalEnds(this, this.ends.toOneOrMore, other, other.ends.toOneOrMore)
    else false
  }

  override protected def baseHashCode: Int = ends.foldLeft(0)(_ ^ _.hashCode)
}

/** Equality for targets handled as a $BAG.
  *  Targets are equal if they contain the same nodes irrespective of their position.
  */
protected[collection] trait EqDiHyper extends Eq {
  this: AnyDiHyperEdge[_] =>

  override protected def baseEquals(other: Edge[_]): Boolean = {
    val (thisArity, thatArity) = (arity, other.arity)
    if (thisArity == thatArity)
      if (thisArity == 2)
        this.sources.head == other.sources.head &&
        this.targets.head == other.targets.head
      else
        other match {
          case diHyper: AnyDiHyperEdge[_] =>
            Eq.equalEnds(this, this.sources, diHyper, diHyper.sources) &&
            Eq.equalEnds(this, this.targets, diHyper, diHyper.targets)
          case _ => false
        }
    else false
  }

  override protected def baseHashCode: Int = {
    var m = 4

    def mul(i: Int): Int = { m += 3; m * i }
    ends.foldLeft(0)((s: Int, n: Any) => s ^ mul(n.hashCode))
  }
}

protected[collection] trait EqUnDi[+N] extends Eq {
  this: AnyUnDiEdge[N] =>

  @inline final protected def unDiBaseEquals(n1: Any, n2: Any): Boolean =
    this.node1 == n1 && this.node2 == n2 ||
      this.node1 == n2 && this.node2 == n1

  override protected def baseEquals(other: Edge[_]): Boolean = other match {
    case edge: AnyEdge[_] => unDiBaseEquals(edge.node1, edge.node2)
    case hyper: AnyHyperEdge[_] if hyper.isUndirected && hyper.arity == 2 =>
      unDiBaseEquals(hyper.node(0), hyper.node(1))
    case _ => false
  }

  override protected def baseHashCode: Int = node1.## ^ node2.##
}

protected[collection] trait EqDi[+N] extends Eq {
  this: AnyDiEdge[N] =>

  @inline final protected def diBaseEquals(n1: Any, n2: Any): Boolean =
    this.source == n1 &&
      this.target == n2

  final override protected def baseEquals(other: Edge[_]): Boolean = other match {
    case edge: AnyDiEdge[_]                           => diBaseEquals(edge.source, edge.target)
    case hyper: AnyDiHyperEdge[_] if hyper.arity == 2 => diBaseEquals(hyper.sources.head, hyper.targets.head)
    case _                                            => false
  }

  override protected def baseHashCode: Int = 23 * node1.## ^ node2.##
}

/** Defines how to handle the ends of hyperedges, or the source/target ends of directed hyperedges,
  *  with respect to equality.
  */
sealed abstract class CollectionKind(val duplicatesAllowed: Boolean, val orderSignificant: Boolean)

object CollectionKind {
  protected[collection] def from(duplicatesAllowed: Boolean, orderSignificant: Boolean): CollectionKind =
    if (duplicatesAllowed)
      if (orderSignificant) Sequence else Bag
    else
      throw new IllegalArgumentException("'duplicatesAllowed == false' is not supported for endpoints kind.")

  protected[collection] def from(s: String): CollectionKind =
    if (s == Bag.toString) Bag
    else if (s == Sequence.toString) Sequence
    else throw new IllegalArgumentException(s"Unexpected representation of '$s' for endpoints kind.")

  protected[collection] def from(edge: Edge[_]): CollectionKind =
    CollectionKind.from(duplicatesAllowed = true, orderSignificant = edge.isInstanceOf[OrderedEndpoints])

  def unapply(kind: CollectionKind): Option[(Boolean, Boolean)] =
    Some((kind.duplicatesAllowed, kind.orderSignificant))
}

/** Marks a hyperedge, $ORDIHYPER, to handle the endpoints
  *  as an unordered collection of nodes with duplicates allowed.
  */
case object Bag extends CollectionKind(true, false)

/** Marks a hyperedge, $ORDIHYPER, to handle the endpoints
  *  as an ordered collection of nodes with duplicates allowed.
  */
case object Sequence extends CollectionKind(true, true)

/** Marks (directed) hyperedge endpoints to have a significant order. */
protected[collection] trait OrderedEndpoints

/** Edge mixin for multigraph support.
  *
  * As a default, hashCode/equality of edges is determined by their ends.
  * We say that edge ends are part of the edge key.
  *
  * Whenever your custom edge needs to be a multi-edge, meaning that your graph is allowed
  * to connect some nodes by an instance of this edge even if those nodes are already connected,
  * the edge key needs be extended by adding at least one more class member, constant or whatsoever to the edge key.
  *
  * For example edges representing flight connections between airports need be multi-edges to allow for different
  * flights between the same airports.
  *
  * @author Peter Empen
  */
trait MultiEdge extends Eq { this: Edge[_] =>

  /** Each value, such as a class member, you specify in this non-empty sequence will be added to the edge "key"
    * which contains the edge `ends`, in terms of a directed edge the `source` and the `target`, by default.
    * The term "key" refers to how equality of edges is controlled.
    */
  def extendKeyBy: OneOrMore[Any]

  override def equals(other: Any): Boolean =
    super.equals(other) && (other match {
      case that: MultiEdge => this.extendKeyBy == that.extendKeyBy
      case _               => false
    })

  override def hashCode: Int = super.hashCode + extendKeyBy.map(_.## * 41).iterator.sum
}
