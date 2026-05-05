
public final class AnnotationType
{

 @MyMarker
 @MyValued("hello")
 public static void main(String[] args)
 {
  System.out.println("ok");
 }
}

@interface MyMarker
{
}

@interface MyValued
{
 String value();
 int count() default 0;
}
