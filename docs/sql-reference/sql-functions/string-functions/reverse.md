# reverse

## Description

This function returns a string with the characters in reverse order.

## Syntax

```Haskell
VARCHAR reverse(VARCHAR str)
```

## Examples

```Plain Text
MySQL > SELECT REVERSE('hello');
+------------------+
| REVERSE('hello') |
+------------------+
| olleh            |
+------------------+
1 row in set (0.00 sec)

MySQL > SELECT REVERSE('你好');
+------------------+
| REVERSE('你好')  |
+------------------+
| 好你             |
+------------------+
1 row in set (0.00 sec)
```

## keyword

REVERSE
