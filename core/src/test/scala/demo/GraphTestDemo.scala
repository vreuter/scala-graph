package demo

import scalax.collection.edges._
import scalax.collection.mutable.Graph

/** Includes the examples given on [[http://www.scala-graph.org/guides/test.html
  *  Test Utilities]].
  */
object GraphTestDemo extends App {

  import scalax.collection.generator._

  object PersonData {
    val firstNames     = Vector("Alen", "Alice", "Bob", "Jack", "Jim", "Joe", "Kate", "Leo", "Tim", "Tom")
    val firstNamesSize = firstNames.size

    val surnames     = Vector("Bell", "Brown", "Clark", "Cox", "King", "Lee", "Moore", "Ross", "Smith", "Wong")
    val surnamesSize = surnames.size

    def order          = firstNamesSize * surnamesSize / 10
    def degrees        = new NodeDegreeRange(2, order / 2)
    val maxYearOfBirth = 2010
  }

  object RG {
    // working with random graph generators -----------------------------------------------

    // obtaining generators for graphs with predefined metrics
    val predefined = RandomGraph.tinyConnectedIntDi(Graph)
    val tinyGraph  = predefined.draw // Graph[Int,DiEdge]

    // setting individual graph metrics while keeping metrics constraints in mind
    object sparse_1000_Int extends RandomGraph.IntFactory {
      val order              = 1000
      val nodeDegrees        = NodeDegreeRange(1, 10)
      override def connected = false
    }
    val randomSparse = RandomGraph.fromMetrics[Int, UnDiEdge[Int], Graph](Graph, sparse_1000_Int, Set(UnDiEdge))
    val sparseGraph  = randomSparse.draw // Graph[Int,UnDiEdge]

    // TODO obtain generators for graphs with typed edges

    case class Person(name: String, yearOfBirth: Int)
    object Person {
      import PersonData._
      private val r = new scala.util.Random

      def drawName: String = {
        def drawFirstName: String = firstNames(r.nextInt(firstNamesSize))
        def drawSurame: String    = surnames(r.nextInt(surnamesSize))

        s"$drawFirstName, $drawSurame"
      }

      def drawYearOfBirth = maxYearOfBirth - r.nextInt(100)
    }

    val randomMixedGraph =
      RandomGraph.fromMetrics[Person, UnDiEdge[Person], Graph](
        Graph,
        new RandomGraph.Metrics[Person] {
          val order           = PersonData.order
          val nodeDegrees     = PersonData.degrees
          def nodeGen: Person = Person(Person.drawName, Person.drawYearOfBirth)
        },
        Set(UnDiEdge) // TODO LDiEdge
      )
    val mixedGraph = randomMixedGraph.draw
    /*
    println(mixedGraph)
    Graph(
        Person(Alice, Smith,1967),
        Person(Kate, Ross,1921),
        Person(Leo, Bell,2008),
        Person(Leo, Smith,1983),
        ...,
        Person(Alice, Smith,1967)~>Person(Kate, Ross,1921) 'C,
        Person(Leo, Bell,2008)~Person(Leo, Smith,1983),
        ...
    )
     */
  }

  object GG {
    import org.scalacheck.{Arbitrary, Gen}
    import Arbitrary.arbitrary
    import org.scalacheck.Prop.forAll

    // working with org.scalacheck.Arbitrary graphs ---------------------------------------

    // obtaining Arbitrary instances for graphs with predefined metrics
    type IntDiGraph = Graph[Int, DiEdge[Int]]
    implicit val arbitraryTinyGraph: Arbitrary[Graph[Int, DiEdge[Int]]] = GraphGen.tinyConnectedIntDi[Graph](Graph)

    val properTiny = forAll(arbitrary[IntDiGraph]) { (g: IntDiGraph) =>
      g.order == GraphGen.TinyInt.order
    }
    properTiny.check()

    // setting individual graph metrics for Arbitrary instances
    // while keeping metrics constraints in mind
    object Sparse_1000_Int extends GraphGen.Metrics[Int] {
      val order              = 1000
      val nodeDegrees        = NodeDegreeRange(1, 10)
      def nodeGen: Gen[Int]  = Gen.choose(0, 10 * order)
      override def connected = false
    }

    type IntUnDiGraph = Graph[Int, UnDiEdge[Int]]
    implicit val arbitrarySparseGraph: Arbitrary[Graph[Int, UnDiEdge[Int]]] = Arbitrary {
      GraphGen.fromMetrics[Int, UnDiEdge[Int], Graph](Graph, Sparse_1000_Int, Set(UnDiEdge)).apply
    }

    val properSparse = forAll(arbitrary[IntUnDiGraph]) { (g: IntUnDiGraph) =>
      g.order == Sparse_1000_Int.order
    }
    properSparse.check()

    // TODO obtain Arbitrary instances to generate graphs with typed edges

    case class Person(name: String, yearOfBirth: Int)
    object Person {
      import PersonData._

      def firstNameGen: Gen[String] = Gen.oneOf(firstNames)
      def surameGen: Gen[String]    = Gen.oneOf(surnames)

      def nameGen: Gen[String] = Gen.resultOf((firstName: String, surname: String) => s"$firstName, $surname")(
        Arbitrary(firstNameGen),
        Arbitrary(surameGen)
      )

      def yearOfBirthGen: Gen[Int] = Gen.choose(maxYearOfBirth - 100, maxYearOfBirth)
    }

    object MixedMetrics extends GraphGen.Metrics[Person] {
      val order       = PersonData.order
      val nodeDegrees = PersonData.degrees
      def nodeGen: Gen[Person] = Gen.resultOf((name: String, year: Int) => Person(name, year))(
        Arbitrary(Person.nameGen),
        Arbitrary(Person.yearOfBirthGen)
      )
    }

    type Mixed = Graph[Person, UnDiEdge[Person]]
    implicit val arbitraryMixedGraph: Arbitrary[Graph[Person, UnDiEdge[Person]]] = Arbitrary {
      GraphGen
        .fromMetrics[Person, UnDiEdge[Person], Graph](Graph, MixedMetrics, Set(UnDiEdge /*, TODO LDiEdge*/ ))
        .apply
    }

    val properMixedGraph = forAll(arbitrary[Mixed]) { (g: Mixed) =>
      g.order == MixedMetrics.order
    }
    properMixedGraph.check()
    // println(arbitraryMixedGraph.arbitrary.sample)

    // Integrating with ScalaTest, limiting the minimum # of successful tests
    import org.scalatest.matchers.should.Matchers
    import org.scalatest.refspec.RefSpec
    import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

    class TGraphGenTest extends RefSpec with Matchers with ScalaCheckPropertyChecks {

      implicit val config: PropertyCheckConfiguration =
        PropertyCheckConfiguration(minSuccessful = 5, maxDiscardedFactor = 1.0)

      object `generated Tiny graph` {
        implicit val arbitraryTinyGraph: Arbitrary[Graph[Int, DiEdge[Int]]] = GraphGen.tinyConnectedIntDi[Graph](Graph)

        def `should conform to tiny metrics`: Unit =
          forAll(arbitrary[IntDiGraph]) { (g: IntDiGraph) =>
            g.order should equal(GraphGen.TinyInt.order)
          }
      }
    }
  }

  RG
  GG
}
