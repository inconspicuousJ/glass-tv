"""Original landscape wallpapers: lit fractal mountains, snow, haze, stars, still-water reflections.
Also writes a cut-out of the front mountains (for the clock depth effect)."""
import numpy as np, sys
from PIL import Image, ImageFilter
W,H=1920,1080
yy,xx=np.mgrid[0:H,0:W].astype(np.float32)

def col(h): h=h.lstrip('#'); return np.array([int(h[i:i+2],16) for i in (0,2,4)],np.float32)
def mix(a,b,t): return a+(b-a)*t

def fbm(seed,scale,oct=6,gain=0.5,w=W,h=H):
    rng=np.random.default_rng(seed); out=np.zeros((h,w),np.float32); amp=1; tot=0; s=scale
    for o in range(oct):
        gw,gh=max(2,int(w/s)+2),max(2,int(h/s)+2)
        g=rng.random((gh,gw)).astype(np.float32)
        im=Image.fromarray(g).resize((w,h),Image.BICUBIC)
        out+=np.asarray(im)*amp; tot+=amp; amp*=gain; s/=2
    return out/tot

def ridge_line(seed,base,amp,rough=0.52,peaks=1.0):
    rng=np.random.default_rng(seed); n=2**10+1; y=np.zeros(n)
    y[0],y[-1]=rng.normal(0,1,2); step=n-1; sc=1.0
    while step>1:
        h=step//2
        for i in range(h,n-1,step): y[i]=(y[i-h]+y[i+h])/2+rng.normal(0,sc)
        step=h; sc*=rough
    y=(y-y.min())/(y.max()-y.min()+1e-9); y=y**peaks
    return np.interp(np.linspace(0,n-1,W),np.arange(n),y).astype(np.float32)*-amp*H+base*H

def render(name, P, seed, fg_layer):
    rng=np.random.default_rng(seed)
    t=(yy/H)[...,None]
    sky=mix(col(P['sky0']),col(P['sky1']),np.clip(t/0.62,0,1)**1.3)
    # glow (sun / moon haze)
    gx,gy,gr,gs=P['glow']
    d=np.sqrt(((xx-gx*W)/W)**2+((yy-gy*H)/H*1.6)**2)
    sky=sky+(col(P['glowc'])-sky)*(np.exp(-(d/gr)**2)*gs)[...,None]
    # soft clouds
    if P.get('clouds',0)>0:
        c=fbm(seed+5,520,5); c=np.clip((c-0.55)*2.2,0,1)*np.clip(1-yy/(H*0.55),0,1)
        sky=sky+(col(P['cloudc'])-sky)*(c*P['clouds'])[...,None]
    img=sky.copy()
    if P.get('stars',0):
        n=P['stars']; sx=rng.integers(0,W,n); sy=(rng.random(n)**1.8*H*0.5).astype(int); b=rng.uniform(30,190,n)
        for x,y,v in zip(sx,sy,b): img[y,x]+=v; 
        img=np.minimum(img,255)
    if P.get('moon'):
        mx,my,mr=P['moon']; md=np.sqrt((xx-mx*W)**2+(yy-my*H)**2)
        img=img+(np.array([235,240,236],np.float32)-img)*np.clip(mr-md,0,1)[...,None]
        img=img+(col(P['glowc'])-img)*(np.exp(-(md/(mr*6))**2)*0.35)[...,None]
    light=np.array(P['light'],np.float32); light/=np.linalg.norm(light)
    ridges=[]
    nL=len(P['layers'])
    for i,L in enumerate(P['layers']):
        r=ridge_line(seed*13+i,L['base'],L['amp'],L.get('rough',0.52),L.get('peaks',1.0))
        if L.get('centre'): r=r-L['centre']*H*np.exp(-((np.arange(W)/W-0.5)/0.16)**2).astype(np.float32)
        ridges.append(r)
        inside=yy>=r[None,:]
        depth=np.clip((yy-r[None,:])/(H*max(L['amp'],0.05)),0,4)
        # clean faceted light: faces turned towards the light are brighter, fading with depth
        k_=np.exp(-np.arange(-90,91)**2/(2*30.0**2)); k_/=k_.sum(); sl=np.gradient(np.convolve(np.pad(r,90,mode='edge'),k_,mode='valid'))*3
        face=np.clip(-sl*light[0]*0.9,-1,1)[None,:]
        lam=np.clip(0.5+face*np.exp(-depth*1.4),0,1)
        lam=np.asarray(Image.fromarray((lam*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(26)),np.float32)/255
        base=col(L['c'])
        lit=base*(0.62+0.75*lam[...,None])*(1-0.25*np.clip(depth,0,1)[...,None])
        if L.get('snow',0)>0:
            band=np.exp(-depth/(L['snow']*0.35))
            sn=np.clip(band*(0.35+lam)*1.3-0.55,0,1)
            sn=np.asarray(Image.fromarray((sn*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(8)),np.float32)/255
            lit=mix(lit,col(P['snowc'])*(0.55+0.55*lam[...,None]),sn[...,None])
        # rim light on the crest from the glow
        crest=np.exp(-((yy-r[None,:])/1.6)**2)*L.get('rim',0.0)*0.6
        lit=lit+(col(P['glowc'])-lit)*crest[...,None]
        # atmospheric perspective: far layers fade into the sky colour behind them
        haze=L.get('haze',0)
        lit=mix(lit,sky,haze*np.clip(1-depth*0.6,0.4,1)[...,None])
        m=np.asarray(Image.fromarray((inside*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.9)),np.float32)[...,None]/255
        img=img*(1-m)+lit*m
        # mist pooling in the valley behind the next layer
        if L.get('mist',0)>0:
            mb=np.exp(-((yy-(r.mean()+H*0.05))/(H*0.06))**2)*(0.6+0.4*fbm(seed+i*7,700,2))*L['mist']*1.1
            img=img+(col(P['mistc'])-img)*np.clip(mb,0,1)[...,None]
    if P.get('water'):
        hz=int(P['water']*H)
        top=img[:hz].copy(); refl=top[::-1][:H-hz]
        out=np.empty_like(refl); ph=rng.uniform(0,6)
        for row in range(refl.shape[0]):
            k=row/refl.shape[0]
            out[row]=np.roll(refl[row],int(np.sin(row*0.35+ph)*(0.3+3*k)),axis=0)
        out=np.asarray(Image.fromarray(np.clip(out,0,255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.6)),np.float32)
        k=np.linspace(0,1,out.shape[0],dtype=np.float32)[:,None,None]
        out=mix(out*0.78,col(P['sky0'])*0.6,np.clip(k*0.9,0,1)*0.55)
        # faint ripple sparkle
        img[hz:]=out
    # vignette + grain
    v=1-0.42*(((xx/W-0.5)**2)*1.2+((yy/H-0.42)**2)*1.6)
    img=img*np.clip(v,0.5,1)[...,None]+rng.normal(0,1.8,img.shape)
    out8=np.clip(img,0,255).astype(np.uint8)
    Image.fromarray(out8).save(name+'.jpg',quality=88,optimize=True)
    r=ridges[fg_layer]
    m=Image.fromarray(((yy>=r[None,:]-0.5)*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    rgba=Image.fromarray(out8).convert('RGBA'); rgba.putalpha(m)
    rgba.resize((1280,720),Image.LANCZOS).save(name+'_fg.webp','WEBP',quality=84,method=6)
    print(name,'front ridge: top',int(r.min()),'centre min',int(r[700:1220].min()))

PRESETS={
 'lake':dict(sky0='#04090b',sky1='#1d3a35',glowc='#a8d8c8',glow=(0.58,0.40,0.32,0.55),cloudc='#3e5f58',clouds=0.25,
   stars=500,moon=(0.70,0.17,9),light=(-0.6,-0.7,0.5),snowc='#e8f2ee',mistc='#9fc7bb',water=0.66,
   layers=[dict(c='#3c5a55',base=0.42,amp=0.20,snow=0.55,haze=0.45,rim=0.5,detail=70,peaks=1.3),
           dict(c='#20332f',base=0.60,amp=0.20,snow=0.35,haze=0.12,rim=0.35,mist=0.25,detail=90,peaks=1.2,centre=0.17),
           dict(c='#0d1715',base=0.665,amp=0.05,haze=0.0,detail=60)]),
 'ridge':dict(sky0='#02050d',sky1='#2a4170',glowc='#b9cdf5',glow=(0.32,0.46,0.34,0.65),cloudc='#4a5f8a',clouds=0.3,
   stars=650,light=(-0.7,-0.6,0.5),snowc='#eef3ff',mistc='#8ea4d0',
   layers=[dict(c='#4a5f8c',base=0.40,amp=0.22,snow=0.7,haze=0.5,rim=0.6,detail=60,peaks=1.4),
           dict(c='#283a5e',base=0.60,amp=0.24,snow=0.4,haze=0.2,rim=0.4,mist=0.3,detail=80,peaks=1.2),
           dict(c='#141f36',base=0.80,amp=0.16,haze=0.05,mist=0.2,detail=80),
           dict(c='#070b15',base=0.95,amp=0.10,detail=80)]),
 'dusk':dict(sky0='#150c1a',sky1='#d3846a',glowc='#ffc58f',glow=(0.52,0.62,0.30,0.85),cloudc='#c67a74',clouds=0.45,
   stars=0,light=(0.2,-0.8,0.6),snowc='#ffd9c0',mistc='#d79a8a',water=0.68,
   layers=[dict(c='#6a4458',base=0.50,amp=0.18,snow=0.5,haze=0.45,rim=0.7,detail=70,peaks=1.3),
           dict(c='#3a2232',base=0.63,amp=0.16,snow=0.25,haze=0.15,rim=0.6,detail=80,peaks=1.15,centre=0.19),
           dict(c='#1a0f17',base=0.685,amp=0.05,detail=60)]),
 'mist':dict(sky0='#0f1513',sky1='#5c6e66',glowc='#dfe9e3',glow=(0.5,0.38,0.5,0.4),cloudc='#7d8f87',clouds=0.3,
   stars=0,light=(-0.5,-0.8,0.5),snowc='#f0f5f2',mistc='#c9d6cf',
   layers=[dict(c='#55665f',base=0.42,amp=0.18,snow=0.5,haze=0.6,rim=0.3,mist=0.5,detail=70,peaks=1.3),
           dict(c='#33423c',base=0.62,amp=0.16,snow=0.3,haze=0.35,rim=0.25,mist=0.5,detail=80,peaks=1.2,centre=0.25),
           dict(c='#1c2622',base=0.78,amp=0.16,haze=0.15,mist=0.4,detail=80),
           dict(c='#0b100e',base=0.94,amp=0.10,detail=80)]),
}
SEEDS={'lake':3,'ridge':8,'dusk':11,'mist':19}
FG={'lake':1,'ridge':1,'dusk':1,'mist':1}
for n in (sys.argv[1:] or PRESETS): render(n,PRESETS[n],SEEDS[n],FG[n])
