# Error Tracking Is Commercially Worthless

Well, no, I don't actually think that, the title is just clickbait. But bear with me, I do have a point to make.

## Effect Tracking

The recent discussions about Scala 3's experimental [capture checking](https://dotty.epfl.ch/docs/reference/experimental/cc.html), and [whether or not](https://medium.com/@odomontois/several-days-ago-we-saw-the-new-experimental-feature-called-capture-checking-was-announced-in-e4aa9bc4b3d1) this has anything to do with effect tracking, reminded me about a blogpost by John De Goes from a couple of years ago titled "[Effect Tracking Is Commercially Worthless](https://degoes.net/articles/no-effect-tracking)". The post raises some interesting points, and I recommend that you go ahead and read it for some context. The gist of it is that effect tracking is not sufficiently commercially useful to justify the developer overhead.

Setting aside for the moment the question of whether I agree with this general sentiment or not, something about the arguments in the post just doesn't work for me. In the past, I couldn't quite place my finger on it. Now I think I can finally formulate what bothers me: a similar argument can be made against other programming features, despite some of them being "obviously"[^1] useful.

To wit, here's [a passage](https://degoes.net/articles/no-effect-tracking#worthless-effect-tracking) from the original blog post, where I replaced the words "effect tracking" with "error tracking", "side effects" with "throwing errors", and "purity" with "totality" (my modifications are in ***bold italics***):  

> It's my contention that ***error*** tracking is worthless precisely because if a developer has any idea about what a function is intended to do, then they already know with a high degree of certainty whether or not the function can ***throw errors*** (assuming, of course, they are taught what it means for something to "***throw errors***").
>
>No developer who knows what ***error throwing*** is is surprised that ***`Files.read` throws exceptions***, because they already know what the function is intended to do. Indeed, many language features and best practices (as well as IDE features!) are designed precisely to give developers a better idea of what functions are supposed to do.
>
>Given a rudimentary understanding of what a function's purpose, the ***totality*** of the function can be predicted with high probability (violations, like [***covariant array insertion***](https://www.baeldung.com/java-arraystoreexception)[^2], become legendary!). However, given the ***totality*** of a function, this information by itself does not convey any useful information on the function's purpose.
>
>Stated simply, ***error*** tracking isn't incredibly useful in practice, because when it matters (and it doesn't always matter), we already know roughly what functions do, and therefore, whether or not they ***throw errors***.
>
>Whatever benefit ***error*** tracking would have would be overwhelmed by the cost of ceremony and boilerplate—this is doubly-true for the "fine-grained" variants.
>
>Moreover, even assuming, evidence to the contrary notwithstanding, that ***error*** tracking was a killer feature, it could be baked into an IDE without any modifications to a language's syntax or semantics.
>
>One could imagine clicking on an "***exceptions***" button next to a function, ***and seeing all the possible errors a function can throw***. A ***totality*** tooling solution like this would have only upside, because it would not require mindless and verbose boilerplate and ceremony.

The modified passage reads pretty much the same as the original. If one finds the original argument against effect tracking convincing, I would posit that same would apply to error tracking. Even more so, as in the new formulation the fictional and commercially worthless "[Effect-Tracked Java™](https://degoes.net/articles/no-effect-tracking#effect-tracked-java)" becomes "Error-Tracked Java", which is literally just plain Java with its checked exceptions. Seeing how the mainstream (e.g., C#, and even [some](https://softwareengineering.stackexchange.com/questions/121328/is-it-good-practice-to-catch-a-checked-exception-and-throw-a-runtimeexception) Java programmers) moved away from checked exceptions, one might conclude that error tracking is indeed commercially worthless.

## Commercially Worthless?

So is error tracking a commercially worthless endeavor? Given ZIO's [dedicated error channel](https://degoes.net/articles/bifunctor-io), it doesn't seem that John would agree.

What can we learn from all this? I'm not sure. 

Do I want to use Java (or Go[^3], *gasp*) as a guide to programming features we should adopt? If we go down that road, adapting the same arguments to other nice features would be straightforward. Should we give up on things like null tracking, immutability, and referential transparency? Seeing how I'm using Scala, mainstream adoption is probably not my top priority, and the answer to the preceding question would be a "no" from me[^4]. And so, we end up with a non-argument against effect tracking.

## A Red Herring

To leave things on a more positive note, I do have a pet theory.

According to that theory, what is actually going on here is that ZIO is a surreptitious attempt to bring tracked effects into the mainstream. The argument against effect tracking is just a diversion, to lure in unsuspecting Java developers into an [unescapable](https://github.com/zio/zio/blob/0c264f02161708ccfd3ead630f4ca107b372a9b1/core/shared/src/main/scala/zio/Runtime.scala#L58) effect container, a "gateway drug" of sorts. While in reality, a fine-grained algebraic effect system is [upon us](https://twitter.com/jdegoes/status/1492248469762883587):
> ... a demonstration that, as many have suspected, ZIO has gradually acquired the power of an algebraic effect system, with layers serving as effect handlers, which interpret from effect to effect.

And we all know now that a `ReaderT[IO]` is just a [few of steps away](https://xn--i2r.xn--rhqv96g/2022/02/03/readert-is-extensible-effects/) from a full-blown extensible effects system...

### Discussion

- [r/scala thread](https://www.reddit.com/r/scala/comments/szmg95/error_tracking_is_commercially_worthless/)

[^1]: By that I mean that some would agree that the usefulness is obvious, of course nothing is ever (objectively) obvious, and each feature requires its own justification.
[^2]: I couldn't find a more stark example of a surprising exception in Java, but it's not quite as catchy as the original `java.net.URL#hashCode/equals` example. If you're aware of some other surprising Java exceptions, please let me know.
[^3]: Though I can think of [other reasons](https://slatestarcodex.com/2017/11/30/book-review-inadequate-equilibria/) to ignore the mainstream.
[^4]: The lack of an argument against a feature is definitely not argument for it. But I will leave the justification of these features for another time.
