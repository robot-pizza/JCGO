// JCGO-SKIP: this fixture is intentionally invalid — it exists to
// prove slice 52's enforcement fires. `Shape` is sealed and permits
// only `Circle`; declaring `Square` as another implementer should
// reject at translate time.

public final class SealedBad
{

 sealed interface Shape permits Circle {}

 record Circle(double r) implements Shape {}

 // Not in Shape's permits list — should be rejected by slice 52.
 record Square(double s) implements Shape {}

 public static void main(String[] args)
 {
  // Force Square to be reachable so the analyzer loads it and the
  // sealed check fires.
  Shape s = new Square(2.0);
  System.out.println(s);
 }
}
