import CandidateList from "@/components/CandidateList";

export default function Home() {
  return (
    <div className="wrap">
      <nav className="nav">
        <div className="brand"><span className="dot" /> Base&nbsp;Breakouts</div>
        <span className="pill">US + India · weekly EOD</span>
      </nav>

      <header className="hero">
        <h1>Catch the <span className="g">multi-year base breakout</span> before the run.</h1>
        <p>
          Every week we scan ~1,000 US &amp; Indian stocks for the rare setup that precedes the
          biggest moves — a long, quiet base that ignites on volume — then rank them with a
          transparent, walk-forward-validated model. You see exactly why each name qualifies.
        </p>
        <div className="stats">
          <div className="stat"><div className="n"><span className="accent">+31%</span></div>
            <div className="l">top-tier median 1y (out-of-sample)</div></div>
          <div className="stat"><div className="n">75%</div><div className="l">win rate</div></div>
          <div className="stat"><div className="n">9/11</div><div className="l">years top beat bottom</div></div>
        </div>
      </header>

      <section className="sec"><h2>Latest scan</h2><span className="meta">ranked by conviction</span></section>
      <CandidateList />

      <section className="sec"><h2>Why these, and not the rest</h2></section>
      <div className="why">
        <div className="f"><h4>Market regime</h4><p>Only fires when the index is above its 40-week trend. Breakouts in falling markets were toxic in testing — we skip them.</p></div>
        <div className="f"><h4>Don&apos;t chase</h4><p>The model penalizes names already extended far above resistance — the best entries are near the breakout line.</p></div>
        <div className="f"><h4>Quiet base, loud break</h4><p>Years of low-volatility consolidation, then a volume surge of 2×+ on the breakout week.</p></div>
        <div className="f"><h4>Momentum tilt</h4><p>A measured prior-momentum lean — the factor that most improved out-of-sample ranking.</p></div>
      </div>

      <div className="disc">
        <b>Important:</b> This is a technical analytics &amp; screening tool, not investment advice and not a
        recommendation to buy or sell. Past performance and backtests (which carry survivorship and other
        limitations, and missed in 2024) do not guarantee future results. Do your own research.
      </div>

      <footer className="wrap">
        Base Breakouts · analytics, not advice · data delayed (end-of-day).
      </footer>
    </div>
  );
}
