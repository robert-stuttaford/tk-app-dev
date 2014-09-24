# Trapperkeeper App and Dev systems

This code accompanies a blog post I wrote:

<http://www.stuttaford.me/2014/09/24/app-and-dev-services-with-trapperkeeper/>

## Start it up

To simply run it:

```
lein tk
```

To develop, start a repl:

```
lein repl :headless
```

And in a separate terminal:

```
lein cljx auto
```

Connect via nREPL, then:

```clj
(user/reset)
(user/dev-reset)
```

After making any app changes:

```clj
(user/reset)
```
