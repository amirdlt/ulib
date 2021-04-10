package algo;

public @interface Algorithm {
    String SEARCH = "Search";
    String SORT = "Sort";
    String NONE = "None";

    String[] type() default "None";
}
